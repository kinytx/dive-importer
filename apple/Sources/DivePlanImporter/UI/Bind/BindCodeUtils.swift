//
//  BindCodeUtils.swift
//
//  把扫码 / deep link 拿到的字符串归一化成 6 位绑定码。
//  跟 Android `BindCodeUtils.kt` 一致：服务端 `NormalizeCode` 也只取前 6 位数字。
//
//  支持的输入形态：
//    - 纯 6 位数字: "123456"
//    - deep link:   "diveplan://ble-probe/bind?code=123456"
//    - 含杂字符:    "123-456 " "Code 123456" → "123456"
//

import Foundation

func extractBindCode(from raw: String?) -> String? {
    guard let raw, !raw.isEmpty else { return nil }

    // 先尝试 URL 解析
    var text = raw
    if raw.lowercased().hasPrefix("diveplan://"),
       let comps = URLComponents(string: raw),
       let codeItem = comps.queryItems?.first(where: { $0.name == "code" }),
       let v = codeItem.value
    {
        text = v
    }

    let digits = text.filter(\.isASCII).filter(\.isNumber)
    guard digits.count >= 6 else { return nil }
    return String(digits.prefix(6))
}
