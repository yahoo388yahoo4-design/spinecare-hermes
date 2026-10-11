import SwiftUI
import UIKit
import CoreGraphics

/// Shared skeleton + HUD drawing for the live overlay AND the session recorder,
/// so saved videos show exactly what the user saw during the session.
/// Convention: the CGContext passed in is y-DOWN with top-left origin —
/// true for SwiftUI Canvas's cgContext and for UIGraphicsImageRenderer.
/// Landmarks are normalized 0..1 with y top-down (PoseTracker flips Vision's
/// bottom-up y so both paths agree).
enum OverlayRenderer {

    // Neck/shoulder/torso connections in the shared name space.
    private static let conns: [(String, String)] = [
        ("lsho", "rsho"), ("lsho", "lhip"), ("rsho", "rhip"), ("lhip", "rhip"),
        ("lsho", "lelb"), ("rsho", "relb"), ("lelb", "lwr"), ("relb", "rwr"),
        ("lhip", "lknee"), ("rhip", "rknee"), ("lknee", "lank"), ("rknee", "rank"),
    ]

    static func draw(cg: CGContext, size: CGSize, lms: [String: Metrics.LM],
                     cue: String, metrics: String, reps: String, mirror: Bool) {
        func px(_ x: Double) -> CGFloat { CGFloat(mirror ? (1 - x) : x) * size.width }
        func py(_ y: Double) -> CGFloat { CGFloat(y) * size.height }

        let ready = !lms.isEmpty
        let green = CGColor(red: 0.20, green: 0.83, blue: 0.60, alpha: 1)
        let amber = CGColor(red: 0.98, green: 0.75, blue: 0.15, alpha: 1)
        let sky   = CGColor(red: 0.22, green: 0.74, blue: 0.98, alpha: 1)
        let lineColor = ready ? green : amber

        cg.setLineWidth(3)
        cg.setStrokeColor(lineColor)
        for (a, b) in conns {
            guard let la = lms[a], let lb = lms[b], la.conf > 0.5, lb.conf > 0.5 else { continue }
            cg.move(to: CGPoint(x: px(la.x), y: py(la.y)))
            cg.addLine(to: CGPoint(x: px(lb.x), y: py(lb.y)))
            cg.strokePath()
        }
        // Neck line (ear -> shoulder), highlighted
        if let lear = lms["lear"], let lsho = lms["lsho"], lear.conf > 0.5, lsho.conf > 0.5 {
            cg.setLineWidth(5)
            cg.setStrokeColor(sky)
            cg.move(to: CGPoint(x: px(lear.x), y: py(lear.y)))
            cg.addLine(to: CGPoint(x: px(lsho.x), y: py(lsho.y)))
            cg.strokePath()
        }
        cg.setFillColor(lineColor)
        for lm in lms.values where lm.conf > 0.5 {
            cg.fillEllipse(in: CGRect(x: px(lm.x) - 3.5, y: py(lm.y) - 3.5, width: 7, height: 7))
        }

        // HUD: reps top-right
        if !reps.isEmpty {
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 40, weight: .bold),
                .foregroundColor: UIColor(red: 0.20, green: 0.83, blue: 0.60, alpha: 1),
            ]
            let s = NSAttributedString(string: reps, attributes: attrs)
            s.draw(at: CGPoint(x: size.width - s.size().width - 20, y: 20), with: attrs)
        }
        // HUD: cue + metrics bottom band
        if !cue.isEmpty || !metrics.isEmpty {
            let block = NSMutableAttributedString()
            if !cue.isEmpty {
                block.append(NSAttributedString(string: cue + "\n", attributes: [
                    .font: UIFont.systemFont(ofSize: 15, weight: .semibold),
                    .foregroundColor: UIColor.systemYellow]))
            }
            if !metrics.isEmpty {
                block.append(NSAttributedString(string: metrics, attributes: [
                    .font: UIFont.monospacedSystemFont(ofSize: 11, weight: .regular),
                    .foregroundColor: UIColor.white]))
            }
            let bgH = block.size().height + 16
            cg.setFillColor(UIColor.black.withAlphaComponent(0.75).cgColor)
            cg.fill(CGRect(x: 0, y: size.height - bgH, width: size.width, height: bgH))
            block.draw(with: CGRect(x: 12, y: size.height - bgH + 8,
                                    width: size.width - 24, height: bgH),
                       options: [.usesLineFragmentOrigin])
        }
    }

    /// Live SwiftUI preview wrapper.
    static func draw(ctx: inout GraphicsContext, size: CGSize, lms: [String: Metrics.LM],
                     cue: String, metrics: String, reps: String, mirror: Bool) {
        ctx.drawLayer { layer in
            draw(cg: layer.cgContext, size: size, lms: lms, cue: cue, metrics: metrics,
                 reps: reps, mirror: mirror)
        }
    }
}
