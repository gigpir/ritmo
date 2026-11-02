import SwiftUI
import UIKit

@main
struct RitmoApp: App {
    @State private var store = WorkoutStore()

    init() {
        // No workout is running at launch, so any Live Activity still on screen is a leftover.
        LiveActivityController.endAll()
        NotificationCenter.default.addObserver(forName: UIApplication.willTerminateNotification, object: nil, queue: .main) { _ in
            LiveActivityController.endAllBeforeExit()
        }
    }

    var body: some Scene {
        WindowGroup {
            WorkoutListView()
                .environment(store)
                .preferredColorScheme(.dark)
                .tint(Step.Phase.work.bright)
        }
    }
}
