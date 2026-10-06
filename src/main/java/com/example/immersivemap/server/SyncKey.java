package com.example.immersivemap.server;

import com.example.immersivemap.map.LayerId;

record SyncKey(LayerId layer, long chunk) {
}
