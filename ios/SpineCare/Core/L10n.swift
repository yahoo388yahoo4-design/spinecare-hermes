import Foundation

/// Localization helper: resolves keys against the bundle for the user-selected
/// language ("system" | "en" | "ro"). Views observe ServerConfig.shared so a
/// language change re-renders immediately.
enum L10n {
    static func bundle() -> Bundle {
        switch ServerConfig.shared.lang {
        case "en": return bundleFor("en")
        case "ro": return bundleFor("ro")
        default:   return .main
        }
    }
    private static func bundleFor(_ code: String) -> Bundle {
        if let path = Bundle.main.path(forResource: code, ofType: "lproj"),
           let b = Bundle(path: path) { return b }
        return .main
    }
    static func t(_ key: String) -> String {
        bundle().localizedString(forKey: key, value: key, table: nil)
    }
    static func t(_ key: String, _ args: CVarArg...) -> String {
        String(format: t(key), arguments: args)
    }
}
