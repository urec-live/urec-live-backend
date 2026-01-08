package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.ExerciseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final EquipmentRepository equipmentRepository;
    private final ExerciseRepository exerciseRepository;

    public DataInitializer(EquipmentRepository equipmentRepository, ExerciseRepository exerciseRepository) {
        this.equipmentRepository = equipmentRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    public void run(String... args) {
        // Only initialize if database is empty
        if (exerciseRepository.count() > 0 || equipmentRepository.count() > 0) {
            System.out.println("⏭️  Database already initialized. Skipping data loading.");
            return;
        }
        
        System.out.println("🔄 Initializing database with gym data...");
        
        // 🏋️ CREATE EXERCISES
        
        // CHEST EXERCISES
        Exercise benchPress = createExercise("Bench Press", "Chest", "https://via.placeholder.com/200x200?text=Bench+Press");
        Exercise inclinePress = createExercise("Incline Press", "Chest", "https://via.placeholder.com/200x200?text=Incline+Press");
        Exercise declinePress = createExercise("Decline Press", "Chest", "https://via.placeholder.com/200x200?text=Decline+Press");
        Exercise chestFly = createExercise("Chest Fly", "Chest", "https://via.placeholder.com/200x200?text=Chest+Fly");
        Exercise cableCrossover = createExercise("Cable Crossover", "Chest", "https://via.placeholder.com/200x200?text=Cable+Crossover");
        Exercise pushups = createExercise("Push-ups", "Chest", "https://via.placeholder.com/200x200?text=Push-ups");
        Exercise chestDips = createExercise("Chest Dips", "Chest", "https://via.placeholder.com/200x200?text=Chest+Dips");
        
        // BACK EXERCISES
        Exercise pullUp = createExercise("Pull Up", "Back", "https://via.placeholder.com/200x200?text=Pull+Up");
        Exercise latPulldown = createExercise("Lat Pulldown", "Back", "https://via.placeholder.com/200x200?text=Lat+Pulldown");
        Exercise seatedCableRow = createExercise("Seated Cable Row", "Back", "https://via.placeholder.com/200x200?text=Seated+Row");
        Exercise bentOverRow = createExercise("Bent Over Row", "Back", "https://via.placeholder.com/200x200?text=Bent+Row");
        Exercise tBarRow = createExercise("T-Bar Row", "Back", "https://via.placeholder.com/200x200?text=T-Bar+Row");
        Exercise deadlift = createExercise("Deadlift", "Back", "https://via.placeholder.com/200x200?text=Deadlift");
        Exercise hyperextension = createExercise("Hyperextension", "Back", "https://via.placeholder.com/200x200?text=Hyperextension");
        Exercise facePull = createExercise("Face Pull", "Back", "https://via.placeholder.com/200x200?text=Face+Pull");
        
        // SHOULDER EXERCISES
        Exercise shoulderPress = createExercise("Shoulder Press", "Shoulders", "https://via.placeholder.com/200x200?text=Shoulder+Press");
        Exercise arnoldPress = createExercise("Arnold Press", "Shoulders", "https://via.placeholder.com/200x200?text=Arnold+Press");
        Exercise lateralRaise = createExercise("Lateral Raise", "Shoulders", "https://via.placeholder.com/200x200?text=Lateral+Raise");
        Exercise frontRaise = createExercise("Front Raise", "Shoulders", "https://via.placeholder.com/200x200?text=Front+Raise");
        Exercise rearDeltFly = createExercise("Rear Delt Fly", "Shoulders", "https://via.placeholder.com/200x200?text=Rear+Delt");
        Exercise uprightRow = createExercise("Upright Row", "Shoulders", "https://via.placeholder.com/200x200?text=Upright+Row");
        Exercise shrugs = createExercise("Shrugs", "Shoulders", "https://via.placeholder.com/200x200?text=Shrugs");
        
        // ARMS - BICEPS
        Exercise bicepCurl = createExercise("Bicep Curl", "Biceps", "https://via.placeholder.com/200x200?text=Bicep+Curl");
        Exercise hammerCurl = createExercise("Hammer Curl", "Biceps", "https://via.placeholder.com/200x200?text=Hammer+Curl");
        Exercise preacherCurl = createExercise("Preacher Curl", "Biceps", "https://via.placeholder.com/200x200?text=Preacher+Curl");
        Exercise cableCurl = createExercise("Cable Curl", "Biceps", "https://via.placeholder.com/200x200?text=Cable+Curl");
        Exercise concentrationCurl = createExercise("Concentration Curl", "Biceps", "https://via.placeholder.com/200x200?text=Concentration");
        
        // ARMS - TRICEPS
        Exercise tricepPushdown = createExercise("Tricep Pushdown", "Triceps", "https://via.placeholder.com/200x200?text=Pushdown");
        Exercise overheadTricepExtension = createExercise("Overhead Tricep Extension", "Triceps", "https://via.placeholder.com/200x200?text=Overhead+Extension");
        Exercise tricepDips = createExercise("Tricep Dips", "Triceps", "https://via.placeholder.com/200x200?text=Tricep+Dips");
        Exercise skullCrusher = createExercise("Skull Crusher", "Triceps", "https://via.placeholder.com/200x200?text=Skull+Crusher");
        Exercise closeGripBench = createExercise("Close Grip Bench", "Triceps", "https://via.placeholder.com/200x200?text=Close+Grip");
        
        // LEGS - QUADS
        Exercise squat = createExercise("Squat", "Quads", "https://via.placeholder.com/200x200?text=Squat");
        Exercise legPress = createExercise("Leg Press", "Quads", "https://via.placeholder.com/200x200?text=Leg+Press");
        Exercise legExtension = createExercise("Leg Extension", "Quads", "https://via.placeholder.com/200x200?text=Leg+Extension");
        Exercise hackSquat = createExercise("Hack Squat", "Quads", "https://via.placeholder.com/200x200?text=Hack+Squat");
        Exercise lunges = createExercise("Lunges", "Quads", "https://via.placeholder.com/200x200?text=Lunges");
        Exercise bulgarianSplitSquat = createExercise("Bulgarian Split Squat", "Quads", "https://via.placeholder.com/200x200?text=Bulgarian");
        
        // LEGS - HAMSTRINGS
        Exercise legCurl = createExercise("Leg Curl", "Hamstrings", "https://via.placeholder.com/200x200?text=Leg+Curl");
        Exercise romanianDeadlift = createExercise("Romanian Deadlift", "Hamstrings", "https://via.placeholder.com/200x200?text=RDL");
        Exercise seatedLegCurl = createExercise("Seated Leg Curl", "Hamstrings", "https://via.placeholder.com/200x200?text=Seated+Curl");
        Exercise lyingLegCurl = createExercise("Lying Leg Curl", "Hamstrings", "https://via.placeholder.com/200x200?text=Lying+Curl");
        
        // LEGS - GLUTES
        Exercise hipThrust = createExercise("Hip Thrust", "Glutes", "https://via.placeholder.com/200x200?text=Hip+Thrust");
        Exercise gluteBridge = createExercise("Glute Bridge", "Glutes", "https://via.placeholder.com/200x200?text=Glute+Bridge");
        Exercise cableKickback = createExercise("Cable Kickback", "Glutes", "https://via.placeholder.com/200x200?text=Kickback");
        Exercise gluteKickbackMachine = createExercise("Glute Kickback Machine", "Glutes", "https://via.placeholder.com/200x200?text=Kickback+Machine");
        
        // LEGS - CALVES
        Exercise calfRaise = createExercise("Calf Raise", "Calves", "https://via.placeholder.com/200x200?text=Calf+Raise");
        Exercise seatedCalfRaise = createExercise("Seated Calf Raise", "Calves", "https://via.placeholder.com/200x200?text=Seated+Calf");
        Exercise legPressCalfRaise = createExercise("Leg Press Calf Raise", "Calves", "https://via.placeholder.com/200x200?text=LP+Calf");
        
        // CORE/ABS
        Exercise crunch = createExercise("Crunch", "Abs", "https://via.placeholder.com/200x200?text=Crunch");
        Exercise plank = createExercise("Plank", "Abs", "https://via.placeholder.com/200x200?text=Plank");
        Exercise legRaise = createExercise("Leg Raise", "Abs", "https://via.placeholder.com/200x200?text=Leg+Raise");
        Exercise russianTwist = createExercise("Russian Twist", "Abs", "https://via.placeholder.com/200x200?text=Russian+Twist");
        Exercise cableCrunch = createExercise("Cable Crunch", "Abs", "https://via.placeholder.com/200x200?text=Cable+Crunch");
        Exercise abWheel = createExercise("Ab Wheel", "Abs", "https://via.placeholder.com/200x200?text=Ab+Wheel");
        Exercise hangingKneeRaise = createExercise("Hanging Knee Raise", "Abs", "https://via.placeholder.com/200x200?text=Knee+Raise");
        Exercise mountainClimber = createExercise("Mountain Climber", "Abs", "https://via.placeholder.com/200x200?text=Mountain+Climber");
        
        // CARDIO
        Exercise running = createExercise("Running", "Cardio", "https://via.placeholder.com/200x200?text=Running");
        Exercise cycling = createExercise("Cycling", "Cardio", "https://via.placeholder.com/200x200?text=Cycling");
        Exercise rowing = createExercise("Rowing", "Cardio", "https://via.placeholder.com/200x200?text=Rowing");
        Exercise stairClimber = createExercise("Stair Climber", "Cardio", "https://via.placeholder.com/200x200?text=Stair+Climber");
        Exercise elliptical = createExercise("Elliptical", "Cardio", "https://via.placeholder.com/200x200?text=Elliptical");
        Exercise jumpRope = createExercise("Jump Rope", "Cardio", "https://via.placeholder.com/200x200?text=Jump+Rope");
        
        // 💪 CREATE EQUIPMENT AND LINK TO EXERCISES
        
        // CHEST EQUIPMENT
        createEquipmentWithExercises("BP001", "Flat Bench Press 1", "Available", "https://via.placeholder.com/100x100?text=FBP1", benchPress);
        createEquipmentWithExercises("BP002", "Flat Bench Press 2", "Available", "https://via.placeholder.com/100x100?text=FBP2", benchPress);
        createEquipmentWithExercises("BP003", "Flat Bench Press 3", "Available", "https://via.placeholder.com/100x100?text=FBP3", benchPress);
        createEquipmentWithExercises("IP001", "Incline Bench 1", "Available", "https://via.placeholder.com/100x100?text=IB1", inclinePress);
        createEquipmentWithExercises("IP002", "Incline Bench 2", "Available", "https://via.placeholder.com/100x100?text=IB2", inclinePress);
        createEquipmentWithExercises("DP001", "Decline Bench", "Available", "https://via.placeholder.com/100x100?text=DB1", declinePress);
        createEquipmentWithExercises("CF001", "Chest Fly Machine 1", "Available", "https://via.placeholder.com/100x100?text=CFM1", chestFly);
        createEquipmentWithExercises("CF002", "Chest Fly Machine 2", "Available", "https://via.placeholder.com/100x100?text=CFM2", chestFly);
        createEquipmentWithExercises("CC001", "Cable Crossover Station 1", "Available", "https://via.placeholder.com/100x100?text=CC1", cableCrossover);
        createEquipmentWithExercises("CC002", "Cable Crossover Station 2", "Available", "https://via.placeholder.com/100x100?text=CC2", cableCrossover);
        createEquipmentWithExercises("DB001", "Dip Station 1", "Available", "https://via.placeholder.com/100x100?text=DS1", chestDips, tricepDips);
        createEquipmentWithExercises("DB002", "Dip Station 2", "Available", "https://via.placeholder.com/100x100?text=DS2", chestDips, tricepDips);
        
        // BACK EQUIPMENT
        createEquipmentWithExercises("PU001", "Pull Up Bar 1", "Available", "https://via.placeholder.com/100x100?text=PU1", pullUp);
        createEquipmentWithExercises("PU002", "Pull Up Bar 2", "Available", "https://via.placeholder.com/100x100?text=PU2", pullUp);
        createEquipmentWithExercises("LP001", "Lat Pulldown 1", "Available", "https://via.placeholder.com/100x100?text=LP1", latPulldown);
        createEquipmentWithExercises("LP002", "Lat Pulldown 2", "Available", "https://via.placeholder.com/100x100?text=LP2", latPulldown);
        createEquipmentWithExercises("LP003", "Lat Pulldown 3", "Available", "https://via.placeholder.com/100x100?text=LP3", latPulldown);
        createEquipmentWithExercises("SR001", "Seated Row 1", "Available", "https://via.placeholder.com/100x100?text=SR1", seatedCableRow);
        createEquipmentWithExercises("SR002", "Seated Row 2", "Available", "https://via.placeholder.com/100x100?text=SR2", seatedCableRow);
        createEquipmentWithExercises("TR001", "T-Bar Row", "Available", "https://via.placeholder.com/100x100?text=TB1", tBarRow);
        createEquipmentWithExercises("DL001", "Deadlift Platform 1", "Available", "https://via.placeholder.com/100x100?text=DL1", deadlift);
        createEquipmentWithExercises("DL002", "Deadlift Platform 2", "Available", "https://via.placeholder.com/100x100?text=DL2", deadlift);
        createEquipmentWithExercises("HE001", "Hyperextension Bench 1", "Available", "https://via.placeholder.com/100x100?text=HE1", hyperextension);
        createEquipmentWithExercises("HE002", "Hyperextension Bench 2", "Available", "https://via.placeholder.com/100x100?text=HE2", hyperextension);
        
        // SHOULDER EQUIPMENT
        createEquipmentWithExercises("SP001", "Shoulder Press 1", "Available", "https://via.placeholder.com/100x100?text=SP1", shoulderPress, arnoldPress);
        createEquipmentWithExercises("SP002", "Shoulder Press 2", "Available", "https://via.placeholder.com/100x100?text=SP2", shoulderPress, arnoldPress);
        createEquipmentWithExercises("SP003", "Shoulder Press 3", "Available", "https://via.placeholder.com/100x100?text=SP3", shoulderPress, arnoldPress);
        createEquipmentWithExercises("LR001", "Lateral Raise Machine", "Available", "https://via.placeholder.com/100x100?text=LR1", lateralRaise);
        createEquipmentWithExercises("RD001", "Rear Delt Fly Machine 1", "Available", "https://via.placeholder.com/100x100?text=RD1", rearDeltFly);
        createEquipmentWithExercises("RD002", "Rear Delt Fly Machine 2", "Available", "https://via.placeholder.com/100x100?text=RD2", rearDeltFly);
        createEquipmentWithExercises("DR001", "Dumbbell Rack 1", "Available", "https://via.placeholder.com/100x100?text=DR1", lateralRaise, frontRaise, shrugs);
        createEquipmentWithExercises("DR002", "Dumbbell Rack 2", "Available", "https://via.placeholder.com/100x100?text=DR2", lateralRaise, frontRaise, shrugs);
        
        // ARM EQUIPMENT
        createEquipmentWithExercises("BC001", "Bicep Curl Machine 1", "Available", "https://via.placeholder.com/100x100?text=BC1", bicepCurl);
        createEquipmentWithExercises("BC002", "Bicep Curl Machine 2", "Available", "https://via.placeholder.com/100x100?text=BC2", bicepCurl);
        createEquipmentWithExercises("PC001", "Preacher Curl Bench 1", "Available", "https://via.placeholder.com/100x100?text=PC1", preacherCurl);
        createEquipmentWithExercises("PC002", "Preacher Curl Bench 2", "Available", "https://via.placeholder.com/100x100?text=PC2", preacherCurl);
        createEquipmentWithExercises("CS001", "Cable Station 1", "Available", "https://via.placeholder.com/100x100?text=CS1", cableCurl, tricepPushdown, facePull);
        createEquipmentWithExercises("CS002", "Cable Station 2", "Available", "https://via.placeholder.com/100x100?text=CS2", cableCurl, tricepPushdown, facePull);
        createEquipmentWithExercises("CS003", "Cable Station 3", "Available", "https://via.placeholder.com/100x100?text=CS3", cableCurl, tricepPushdown, facePull);
        createEquipmentWithExercises("TD001", "Tricep Dip/Press Machine", "Available", "https://via.placeholder.com/100x100?text=TD1", overheadTricepExtension);
        
        // LEG EQUIPMENT
        createEquipmentWithExercises("SQ001", "Squat Rack 1", "Available", "https://via.placeholder.com/100x100?text=SQ1", squat);
        createEquipmentWithExercises("SQ002", "Squat Rack 2", "Available", "https://via.placeholder.com/100x100?text=SQ2", squat);
        createEquipmentWithExercises("SQ003", "Squat Rack 3", "Available", "https://via.placeholder.com/100x100?text=SQ3", squat);
        createEquipmentWithExercises("LPM001", "Leg Press Machine 1", "Available", "https://via.placeholder.com/100x100?text=LPM1", legPress, legPressCalfRaise);
        createEquipmentWithExercises("LPM002", "Leg Press Machine 2", "Available", "https://via.placeholder.com/100x100?text=LPM2", legPress, legPressCalfRaise);
        createEquipmentWithExercises("LPM003", "Leg Press Machine 3", "Available", "https://via.placeholder.com/100x100?text=LPM3", legPress, legPressCalfRaise);
        createEquipmentWithExercises("LE001", "Leg Extension Machine 1", "Available", "https://via.placeholder.com/100x100?text=LE1", legExtension);
        createEquipmentWithExercises("LE002", "Leg Extension Machine 2", "Available", "https://via.placeholder.com/100x100?text=LE2", legExtension);
        createEquipmentWithExercises("LE003", "Leg Extension Machine 3", "Available", "https://via.placeholder.com/100x100?text=LE3", legExtension);
        createEquipmentWithExercises("LC001", "Leg Curl Machine 1", "Available", "https://via.placeholder.com/100x100?text=LC1", legCurl, lyingLegCurl);
        createEquipmentWithExercises("LC002", "Leg Curl Machine 2", "Available", "https://via.placeholder.com/100x100?text=LC2", legCurl, lyingLegCurl);
        createEquipmentWithExercises("SLC001", "Seated Leg Curl 1", "Available", "https://via.placeholder.com/100x100?text=SLC1", seatedLegCurl);
        createEquipmentWithExercises("SLC002", "Seated Leg Curl 2", "Available", "https://via.placeholder.com/100x100?text=SLC2", seatedLegCurl);
        createEquipmentWithExercises("HS001", "Hack Squat Machine 1", "Available", "https://via.placeholder.com/100x100?text=HS1", hackSquat);
        createEquipmentWithExercises("HS002", "Hack Squat Machine 2", "Available", "https://via.placeholder.com/100x100?text=HS2", hackSquat);
        createEquipmentWithExercises("HT001", "Hip Thrust Bench 1", "Available", "https://via.placeholder.com/100x100?text=HT1", hipThrust, gluteBridge);
        createEquipmentWithExercises("HT002", "Hip Thrust Bench 2", "Available", "https://via.placeholder.com/100x100?text=HT2", hipThrust, gluteBridge);
        createEquipmentWithExercises("GK001", "Glute Kickback Machine", "Available", "https://via.placeholder.com/100x100?text=GK1", gluteKickbackMachine);
        createEquipmentWithExercises("CR001", "Standing Calf Raise 1", "Available", "https://via.placeholder.com/100x100?text=CR1", calfRaise);
        createEquipmentWithExercises("CR002", "Standing Calf Raise 2", "Available", "https://via.placeholder.com/100x100?text=CR2", calfRaise);
        createEquipmentWithExercises("SCR001", "Seated Calf Raise 1", "Available", "https://via.placeholder.com/100x100?text=SCR1", seatedCalfRaise);
        createEquipmentWithExercises("SCR002", "Seated Calf Raise 2", "Available", "https://via.placeholder.com/100x100?text=SCR2", seatedCalfRaise);
        
        // CORE/ABS EQUIPMENT
        createEquipmentWithExercises("AM001", "Ab Mat 1", "Available", "https://via.placeholder.com/100x100?text=AM1", crunch, russianTwist);
        createEquipmentWithExercises("AM002", "Ab Mat 2", "Available", "https://via.placeholder.com/100x100?text=AM2", crunch, russianTwist);
        createEquipmentWithExercises("AM003", "Ab Mat 3", "Available", "https://via.placeholder.com/100x100?text=AM3", crunch, russianTwist);
        createEquipmentWithExercises("ACR001", "Ab Crunch Machine", "Available", "https://via.placeholder.com/100x100?text=ACR1", cableCrunch);
        createEquipmentWithExercises("AW001", "Ab Wheel 1", "Available", "https://via.placeholder.com/100x100?text=AW1", abWheel);
        createEquipmentWithExercises("AW002", "Ab Wheel 2", "Available", "https://via.placeholder.com/100x100?text=AW2", abWheel);
        createEquipmentWithExercises("CCH001", "Captain's Chair 1", "Available", "https://via.placeholder.com/100x100?text=CCH1", legRaise, hangingKneeRaise);
        createEquipmentWithExercises("CCH002", "Captain's Chair 2", "Available", "https://via.placeholder.com/100x100?text=CCH2", legRaise, hangingKneeRaise);
        
        // CARDIO EQUIPMENT
        createEquipmentWithExercises("TM001", "Treadmill 1", "Available", "https://via.placeholder.com/100x100?text=TM1", running);
        createEquipmentWithExercises("TM002", "Treadmill 2", "Available", "https://via.placeholder.com/100x100?text=TM2", running);
        createEquipmentWithExercises("TM003", "Treadmill 3", "Available", "https://via.placeholder.com/100x100?text=TM3", running);
        createEquipmentWithExercises("TM004", "Treadmill 4", "Available", "https://via.placeholder.com/100x100?text=TM4", running);
        createEquipmentWithExercises("TM005", "Treadmill 5", "Available", "https://via.placeholder.com/100x100?text=TM5", running);
        createEquipmentWithExercises("TM006", "Treadmill 6", "Available", "https://via.placeholder.com/100x100?text=TM6", running);
        createEquipmentWithExercises("CB001", "Stationary Bike 1", "Available", "https://via.placeholder.com/100x100?text=CB1", cycling);
        createEquipmentWithExercises("CB002", "Stationary Bike 2", "Available", "https://via.placeholder.com/100x100?text=CB2", cycling);
        createEquipmentWithExercises("CB003", "Stationary Bike 3", "Available", "https://via.placeholder.com/100x100?text=CB3", cycling);
        createEquipmentWithExercises("CB004", "Stationary Bike 4", "Available", "https://via.placeholder.com/100x100?text=CB4", cycling);
        createEquipmentWithExercises("RM001", "Rowing Machine 1", "Available", "https://via.placeholder.com/100x100?text=RM1", rowing);
        createEquipmentWithExercises("RM002", "Rowing Machine 2", "Available", "https://via.placeholder.com/100x100?text=RM2", rowing);
        createEquipmentWithExercises("RM003", "Rowing Machine 3", "Available", "https://via.placeholder.com/100x100?text=RM3", rowing);
        createEquipmentWithExercises("STR001", "Stair Climber 1", "Available", "https://via.placeholder.com/100x100?text=STR1", stairClimber);
        createEquipmentWithExercises("STR002", "Stair Climber 2", "Available", "https://via.placeholder.com/100x100?text=STR2", stairClimber);
        createEquipmentWithExercises("EL001", "Elliptical 1", "Available", "https://via.placeholder.com/100x100?text=EL1", elliptical);
        createEquipmentWithExercises("EL002", "Elliptical 2", "Available", "https://via.placeholder.com/100x100?text=EL2", elliptical);
        createEquipmentWithExercises("EL003", "Elliptical 3", "Available", "https://via.placeholder.com/100x100?text=EL3", elliptical);
        createEquipmentWithExercises("EL004", "Elliptical 4", "Available", "https://via.placeholder.com/100x100?text=EL4", elliptical);
        
        // FUNCTIONAL/MISC EQUIPMENT
        createEquipmentWithExercises("BB001", "Barbell Rack 1", "Available", "https://via.placeholder.com/100x100?text=BB1", bentOverRow, uprightRow);
        createEquipmentWithExercises("BB002", "Barbell Rack 2", "Available", "https://via.placeholder.com/100x100?text=BB2", bentOverRow, uprightRow);
        createEquipmentWithExercises("KBR001", "Kettlebell Rack", "Available", "https://via.placeholder.com/100x100?text=KBR1", russianTwist);
        createEquipmentWithExercises("FS001", "Floor Space 1", "Available", "https://via.placeholder.com/100x100?text=FS1", pushups, plank, mountainClimber);
        createEquipmentWithExercises("FS002", "Floor Space 2", "Available", "https://via.placeholder.com/100x100?text=FS2", pushups, plank, mountainClimber);
        createEquipmentWithExercises("FS003", "Floor Space 3", "Available", "https://via.placeholder.com/100x100?text=FS3", pushups, plank, mountainClimber);

        System.out.println("✅ Loaded " + exerciseRepository.count() + " exercises and " + equipmentRepository.count() + " equipment items into database.");
    }
    
    private Exercise createExercise(String name, String muscleGroup, String gifUrl) {
        Exercise exercise = new Exercise(name, muscleGroup, gifUrl);
        return exerciseRepository.save(exercise);
    }
    
    private void createEquipmentWithExercises(String code, String name, String status, String imageUrl, Exercise... exercises) {
        Equipment equipment = new Equipment(code, name, status, imageUrl);
        for (Exercise exercise : exercises) {
            equipment.addExercise(exercise);
        }
        equipmentRepository.save(equipment);
    }
}
