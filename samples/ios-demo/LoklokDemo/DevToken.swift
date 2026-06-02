import Foundation
import CryptoKit

/// Dev-only token minting so the demo is self-contained. In a real app the token comes
/// from YOUR backend after login — never ship the signing secret in a client.
enum DevToken {
    static func mint(userId: String, name: String, secret: String = "dev-secret-change-me") -> String {
        func b64(_ data: Data) -> String {
            data.base64EncodedString()
                .replacingOccurrences(of: "+", with: "-")
                .replacingOccurrences(of: "/", with: "_")
                .replacingOccurrences(of: "=", with: "")
        }
        let header = b64(Data(#"{"alg":"HS256","typ":"JWT"}"#.utf8))
        let exp = Int(Date().timeIntervalSince1970) + 60 * 60 * 24
        let payload = b64(Data(#"{"sub":"\#(userId)","name":"\#(name)","exp":\#(exp)}"#.utf8))
        let signingInput = "\(header).\(payload)"
        let key = SymmetricKey(data: Data(secret.utf8))
        let sig = HMAC<SHA256>.authenticationCode(for: Data(signingInput.utf8), using: key)
        return "\(signingInput).\(b64(Data(sig)))"
    }
}
