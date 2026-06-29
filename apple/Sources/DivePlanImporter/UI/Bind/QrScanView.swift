//
//  QrScanView.swift
//
//  全屏摄像头扫描，识别 QR 后调用 onCodeDetected。
//
//  跨 iOS / macOS：用 AVFoundation 抓 frames + Vision 识别 barcode。
//  preview layer 用 AVCaptureVideoPreviewLayer，桥接靠：
//    - iOS:   UIViewRepresentable + UIView 包 AVCaptureVideoPreviewLayer
//    - macOS: NSViewRepresentable + NSView 包 AVCaptureVideoPreviewLayer
//
//  一次识别成功后立刻停止 session，防止同一码被识别多次。
//

import SwiftUI
import AVFoundation
import Vision

struct QrScanView: View {
    let onCodeDetected: (String) -> Void
    let onCancel: () -> Void

    @State private var permission: PermissionState = .checking
    @State private var detector = QrCaptureCoordinator()

    enum PermissionState { case checking, granted, denied }

    var body: some View {
        ZStack {
            DivePlanColor.background.ignoresSafeArea()

            switch permission {
            case .checking:
                ProgressView("准备相机…")
                    .foregroundStyle(DivePlanColor.textPrimary)
            case .denied:
                permissionDenied
            case .granted:
                cameraScan
            }
        }
        .task {
            permission = await requestCameraPermission() ? .granted : .denied
        }
    }

    private var permissionDenied: some View {
        VStack(spacing: DivePlanSpacing.md) {
            Text("需要相机权限扫码")
                .font(DivePlanFont.title)
                .foregroundStyle(DivePlanColor.textPrimary)
            Text("扫描小程序生成的二维码绑定账号；如果你不想授权，可改用「输入码」")
                .font(DivePlanFont.body)
                .foregroundStyle(DivePlanColor.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, DivePlanSpacing.lg)

            Button("返回输入码", action: onCancel)
                .buttonStyle(.borderedProminent)
                .tint(DivePlanColor.primary)
                .padding(.top, DivePlanSpacing.lg)
        }
        .padding(DivePlanSpacing.xl)
    }

    private var cameraScan: some View {
        ZStack {
            QrCameraPreview(coordinator: detector) { code in
                onCodeDetected(code)
            }
            .ignoresSafeArea()

            VStack {
                Spacer()
                Button("取消扫码", action: onCancel)
                    .buttonStyle(.bordered)
                    .tint(.white)
                    .padding(DivePlanSpacing.xl)
            }
        }
    }

    private func requestCameraPermission() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return true
        case .notDetermined:
            return await AVCaptureDevice.requestAccess(for: .video)
        default: return false
        }
    }
}

// MARK: - Capture coordinator（保持 AVCaptureSession 生命周期）

@MainActor
@Observable
final class QrCaptureCoordinator {
    let session = AVCaptureSession()
    private var detected = false
    var onCode: ((String) -> Void)?

    func start(onCode: @escaping (String) -> Void) {
        guard !session.isRunning else { return }
        self.onCode = onCode
        configureIfNeeded()
        Task.detached { [session] in
            session.startRunning()
        }
    }

    func stop() {
        guard session.isRunning else { return }
        session.stopRunning()
        detected = false
    }

    private var configured = false
    private let metadataOutput = AVCaptureMetadataOutput()

    private func configureIfNeeded() {
        guard !configured else { return }
        configured = true

        session.beginConfiguration()
        defer { session.commitConfiguration() }

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
              ?? AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input)
        else { return }
        session.addInput(input)

        if session.canAddOutput(metadataOutput) {
            session.addOutput(metadataOutput)
        }
        let delegate = MetadataDelegate { [weak self] code in
            guard let self else { return }
            Task { @MainActor in
                guard !self.detected else { return }
                self.detected = true
                self.onCode?(code)
                self.stop()
            }
        }
        metadataOutput.setMetadataObjectsDelegate(delegate, queue: DispatchQueue.global(qos: .userInitiated))
        metadataOutput.metadataObjectTypes = [.qr]
        // 持有 delegate 防止被 ARC 释放
        objc_setAssociatedObject(metadataOutput, &delegateKey, delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    }
}

private var delegateKey: UInt8 = 0

private final class MetadataDelegate: NSObject, AVCaptureMetadataOutputObjectsDelegate {
    let onCode: (String) -> Void
    init(onCode: @escaping (String) -> Void) { self.onCode = onCode }

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              obj.type == .qr,
              let raw = obj.stringValue
        else { return }
        onCode(raw)
    }
}

// MARK: - Camera preview bridge（iOS + macOS）

#if os(iOS)
import UIKit

struct QrCameraPreview: UIViewRepresentable {
    let coordinator: QrCaptureCoordinator
    let onCode: (String) -> Void

    func makeUIView(context: Context) -> PreviewUIView {
        let v = PreviewUIView()
        v.previewLayer.session = coordinator.session
        v.previewLayer.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {
        coordinator.start { code in
            if let normalized = extractBindCode(from: code) {
                onCode(normalized)
            }
        }
    }

    static func dismantleUIView(_ uiView: PreviewUIView, coordinator: ()) {
        // 离开 view 时停 session 由 SwiftUI lifecycle 自然释放
    }
}

final class PreviewUIView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

#elseif os(macOS)
import AppKit

struct QrCameraPreview: NSViewRepresentable {
    let coordinator: QrCaptureCoordinator
    let onCode: (String) -> Void

    func makeNSView(context: Context) -> PreviewNSView {
        let v = PreviewNSView()
        v.previewLayer.session = coordinator.session
        v.previewLayer.videoGravity = .resizeAspectFill
        return v
    }

    func updateNSView(_ nsView: PreviewNSView, context: Context) {
        coordinator.start { code in
            if let normalized = extractBindCode(from: code) {
                onCode(normalized)
            }
        }
    }
}

final class PreviewNSView: NSView {
    let previewLayer = AVCaptureVideoPreviewLayer()
    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer = previewLayer
    }
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        wantsLayer = true
        layer = previewLayer
    }
}
#endif
