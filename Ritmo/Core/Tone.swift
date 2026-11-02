import Foundation

/// Beeps and silence generated as WAV data, so the app needs no sound files.
enum Tone {
    static let sampleRate = 44_100

    static func beep(frequency: Double, seconds: Double) -> Data {
        let count = Int(seconds * Double(sampleRate))
        let ramp = Double(sampleRate) / 200 // 5 ms fade in and out, so the beep doesn't click
        let samples = (0..<count).map { i -> Int16 in
            let t = Double(i) / Double(sampleRate)
            let fade = min(1, Double(i) / ramp, Double(count - i) / ramp)
            return Int16(sin(2 * .pi * frequency * t) * fade * 0.8 * Double(Int16.max))
        }
        return wav(samples)
    }

    static func silence(seconds: Double) -> Data {
        wav([Int16](repeating: 0, count: Int(seconds * Double(sampleRate))))
    }

    /// 16-bit mono PCM in a RIFF container.
    private static func wav(_ samples: [Int16]) -> Data {
        var data = Data()
        func append<T: FixedWidthInteger>(_ value: T) {
            withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) }
        }
        let bytes = UInt32(samples.count * 2)
        data.append(contentsOf: Array("RIFF".utf8))
        append(36 + bytes)
        data.append(contentsOf: Array("WAVEfmt ".utf8))
        append(UInt32(16))
        append(UInt16(1)) // PCM
        append(UInt16(1)) // mono
        append(UInt32(sampleRate))
        append(UInt32(sampleRate * 2))
        append(UInt16(2))
        append(UInt16(16))
        data.append(contentsOf: Array("data".utf8))
        append(bytes)
        for sample in samples { append(sample) }
        return data
    }
}
