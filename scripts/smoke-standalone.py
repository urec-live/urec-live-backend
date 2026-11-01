#!/usr/bin/env python3
"""Local-only smoke check. Creates disposable demo data in run-local-standalone.sh's DB."""
import json
import urllib.error
import urllib.request

ORIGIN = 'http://localhost:8080'
USERNAME = 'standalone_demo'
PASSWORD = 'LocalWorkout2026!'

def request(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(ORIGIN + '/api' + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            raw = response.read()
            if not raw:
                return response.status, None
            try:
                return response.status, json.loads(raw)
            except json.JSONDecodeError:
                return response.status, raw.decode()
    except urllib.error.HTTPError as error:
        return error.code, None

assert request('GET', '/auth/test')[0] == 200
request('POST', '/auth/register', {'username': USERNAME, 'email': 'standalone-demo@example.com', 'password': PASSWORD})
status, auth = request('POST', '/auth/login', {'username': USERNAME, 'password': PASSWORD})
assert status == 200, 'Demo login failed'
token = auth['accessToken']
assert request('DELETE', '/users/me/gym', token=token)[0] == 204
status, current = request('GET', '/users/me/gym', token=token)
assert status == 200 and current is None
status, catalog = request('GET', '/exercises?size=200', token=token)
assert status == 200 and catalog['totalElements'] >= 150
status, directory = request('GET', '/gyms', token=token)
gym = next(item for item in directory if item['name'] == 'UREC')
assert request('PUT', '/users/me/gym', {'gymId': gym['id']}, token)[0] == 200
status, machines = request('GET', '/machines', token=token)
assert status == 200 and machines
assert request('DELETE', '/users/me/gym', token=token)[0] == 204
assert request('GET', '/machines/' + str(machines[0]['id']), token=token)[0] == 403
modes = {
    'WEIGHT_REPS': {'reps': 8, 'weightLbs': 50},
    'REPS': {'reps': 12},
    'DURATION': {'durationSeconds': 45},
    'DISTANCE_DURATION': {'distanceMeters': 1000, 'durationSeconds': 300},
}
for mode, values in modes.items():
    name = 'Demo ' + mode.replace('_', ' ').lower()
    exercise = {'name': name, 'muscleGroup': 'Core', 'equipmentType': 'BODYWEIGHT', 'trainingType': 'STRENGTH', 'loggingMode': mode, 'instructions': 'Disposable example for logging verification.'}
    status, saved = request('POST', '/exercises', exercise, token)
    if status == 409:
        _, results = request('GET', '/exercises?search=Demo', token=token)
        saved = next(item for item in results['content'] if item['name'] == name and item['custom'])
    else:
        assert status == 201
    session = {'exerciseId': saved['id'], 'ownerUsername': USERNAME, 'clientSessionId': 'local-demo-' + mode, 'muscleGroup': 'Core', 'startTime': 1793484000000, 'endTime': 1793484300000, 'setDetails': [values]}
    status, first = request('POST', '/sessions', session, token)
    assert status == 201 and first['machineCode'] is None
    status, retry = request('POST', '/sessions', session, token)
    assert status == 201 and retry['id'] == first['id']
    assert all(retry['setDetails'][0][key] == value for key, value in values.items())
    print('PASS:', mode, 'saved without a machine; retry returns the same session')
_, history = request('GET', '/sessions/me', token=token)
assert history['totalElements'] == 4
assert request('GET', '/users/me/gym', token=token)[1] is None
print('PASS: catalog, optional connection, facility guard, history and all four logging modes')
print('Local demo account:', USERNAME, '/', PASSWORD)
