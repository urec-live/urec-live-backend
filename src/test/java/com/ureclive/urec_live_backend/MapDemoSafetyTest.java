package com.ureclive.urec_live_backend;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class MapDemoSafetyTest {
    @Test void refusesRemotePostgresAndNetworkedH2BeforeReadingOrWritingData() {
        for (String url : new String[]{"jdbc:postgresql://shared.example/urec", "jdbc:h2:tcp://shared.example/urec"}) {
            var env = new MockEnvironment().withProperty("spring.datasource.url", url);
            var seed = new MapDemoInitializer(env, null, null, null, null, null, null, null, null, null);
            assertThrows(IllegalStateException.class, () -> seed.run(new DefaultApplicationArguments()));
        }
    }
}
