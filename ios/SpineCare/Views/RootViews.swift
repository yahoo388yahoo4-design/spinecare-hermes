import SwiftUI

@main
struct SpineCareApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

struct RootView: View {
    var body: some View {
        TabView {
            HomeView().tabItem { Label("Home", systemImage: "house") }
            ExercisesView().tabItem { Label("Exercises", systemImage: "figure.walk") }
            SensorsView().tabItem { Label("Sensors", systemImage: "dot.radiowaves.left.and.right") }
            SettingsView().tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}

struct HomeView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Recovery plan") {
                    Text("C2–C7 disc protrusions, right C5/C7 radiculopathy, mild cervico-dorsal scoliosis, C6 lesion under imaging follow-up.")
                        .font(.callout).foregroundStyle(.secondary)
                }
                Section("Safety") {
                    Text("All plans require clinician sign-off. Stop on: progressive weakness, hand clumsiness, gait change, bowel/bladder change — seek urgent care.")
                        .font(.callout).foregroundStyle(.red)
                }
                Section("Follow-up") {
                    Text("C6 lesion: repeat imaging per radiologist (6–12 weeks). Sensors cannot assess bone or nerve — imaging only.")
                        .font(.callout)
                }
            }
            .navigationTitle("SpineCare")
        }
    }
}

struct ExercisesView: View {
    var body: some View {
        NavigationStack {
            List(Plan.exercises) { ex in
                NavigationLink(value: ex.id) {
                    VStack(alignment: .leading) {
                        Text(ex.name).font(.headline)
                        Text("\(ex.view.uppercased()) view · target \(ex.targetReps) reps · \(ex.clinicianApproved ? "clinician-approved" : "pending approval")")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Exercises")
            .navigationDestination(for: String.self) { id in
                if let ex = Plan.byId(id) { ExerciseSessionView(exercise: ex) }
            }
        }
    }
}
