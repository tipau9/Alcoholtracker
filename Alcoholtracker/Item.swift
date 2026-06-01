//
//  Item.swift
//  Alcoholtracker
//
//  Created by user297483 on 6/1/26.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
