import SwiftUI
import PhotosUI
import UIKit

struct MachineGuideView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(StrengthWorkoutStore.self) private var workoutStore
    let selectedDate: Date
    let defaultGender: Gender
    @State private var image: UIImage?
    @State private var photoItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var result: MachineVisionResult?
    @State private var selectedSlugs = Set<String>()
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var matches: [ExerciseLibraryItem] {
        guard let result, !result.uncertain, !result.searchQuery.isEmpty else { return [] }
        return Array(workoutStore.exerciseLibrary.filtered(levels: [], rawEquipment: [], primaryMuscles: [], secondaryMuscles: [], forces: [], mechanics: [], categories: [], sort: .name, searchText: result.searchQuery).prefix(5))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("EQUIPMENT INTELLIGENCE").font(.caption2.bold()).tracking(1).foregroundStyle(Color.workoutAccent)
                    Text("Meet your machine.").font(.title.bold())
                    Text("Capture the full machine, including handles and seat.").font(.subheadline).foregroundStyle(.secondary)
                    if let image {
                        Image(uiImage: image).resizable().scaledToFit().frame(maxWidth: .infinity, maxHeight: 220)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                    HStack {
                        Button { showCamera = true } label: { Label("Camera", systemImage: "camera") }
                            .disabled(!UIImagePickerController.isSourceTypeAvailable(.camera) || isLoading)
                        PhotosPicker(selection: $photoItem, matching: .images) { Label("Photos", systemImage: "photo") }
                            .disabled(isLoading)
                    }.buttonStyle(.bordered)
                    Text("Analyzing sends the selected photo to your configured AI provider.").font(.caption).foregroundStyle(.secondary)
                    Button {
                        guard let image else { return }
                        Task {
                            isLoading = true; errorMessage = nil; result = nil
                            defer { isLoading = false }
                            do {
                                let found = try await GeminiService.analyzeMachine(image: image)
                                result = found
                                selectedSlugs = BodyAnatomyCatalog.slugs(fromCatalogMuscles: Set(found.primaryMuscles))
                            } catch { errorMessage = error.localizedDescription }
                        }
                    } label: {
                        HStack { Spacer(); if isLoading { ProgressView().tint(.white) } else { Text("Analyze machine").bold() }; Spacer() }
                    }.buttonStyle(.borderedProminent).tint(Color.workoutAccent).disabled(image == nil || isLoading)
                    if let errorMessage { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                    if let result {
                        VStack(alignment: .leading, spacing: 7) {
                            Text(result.uncertain ? "IDENTIFICATION UNCERTAIN" : "IDENTIFICATION CANDIDATE").font(.caption2.bold()).foregroundStyle(Color.workoutAccent)
                            Text(result.name).font(.title3.bold())
                            Text("Verify the label and ask a trainer if unsure.").font(.caption).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity, alignment: .leading).padding(16).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                        if !result.uncertain {
                            if !result.primaryMuscles.isEmpty { Text("Primary: \(result.primaryMuscles.joined(separator: ", "))").font(.subheadline) }
                            if !result.assistingMuscles.isEmpty { Text("Assisting: \(result.assistingMuscles.joined(separator: ", "))").font(.subheadline) }
                            if !selectedSlugs.isEmpty {
                                BiometricBodyMapPanelView(selectedSlugs: $selectedSlugs, dayPlans: workoutStore.dayPlans, defaultGender: defaultGender, matchedCount: matches.count)
                                Text("The map illustrates broad muscle groups; assisting highlights are estimates.").font(.caption).foregroundStyle(.secondary)
                            }
                            if !result.setupCues.isEmpty {
                                Text("Setup and form").font(.headline)
                                ForEach(Array(result.setupCues.enumerated()), id: \.offset) { index, cue in
                                    Text("\(index + 1). \(cue)").font(.subheadline).foregroundStyle(.secondary)
                                }
                                Text("Start light; check the pin or plates. Stop if you feel pain.").font(.caption).foregroundStyle(.secondary)
                            }
                            if !matches.isEmpty {
                                Text("Possible library matches").font(.headline)
                                ForEach(matches) { item in
                                    HStack {
                                        Text(item.name).font(.subheadline); Spacer()
                                        if workoutStore.dayPlans[StrengthWorkoutDate.key(for: selectedDate)]?.exercises.contains(where: { $0.itemID == item.id }) == true {
                                            Text("Added").font(.caption).foregroundStyle(.secondary)
                                        } else {
                                            Button("Add") { workoutStore.toggleExercise(item, on: selectedDate) }.buttonStyle(.bordered)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.padding(20)
            }
            .background(WorkoutBackground())
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: isLoading)
            .navigationTitle("Machine Guide")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let loaded = UIImage(data: data) {
                        image = loaded; result = nil; errorMessage = nil
                    } else { errorMessage = "Unable to open this image." }
                }
            }
            .sheet(isPresented: $showCamera) {
                MachineGuideCamera { photo in
                    if let photo { image = photo; result = nil; errorMessage = nil }
                    showCamera = false
                }.ignoresSafeArea()
            }
        }
    }
}

private struct MachineGuideCamera: UIViewControllerRepresentable {
    let onComplete: (UIImage?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onComplete: onComplete) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onComplete: (UIImage?) -> Void
        init(onComplete: @escaping (UIImage?) -> Void) { self.onComplete = onComplete }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) { onComplete(info[.originalImage] as? UIImage) }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { onComplete(nil) }
    }
}
