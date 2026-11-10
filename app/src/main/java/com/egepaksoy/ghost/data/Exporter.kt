package com.egepaksoy.ghost.data

import java.io.OutputStream
import java.time.Instant
import java.util.Locale

object Exporter {
    fun csv(db: Db, out: OutputStream) = out.bufferedWriter().use { w ->
        w.write("time,lat,lon,accuracy_m\n")
        db.forEachPoint { f ->
            w.write(String.format(Locale.US, "%s,%.6f,%.6f,%.0f\n", Instant.ofEpochMilli(f.time), f.lat, f.lon, f.accuracy))
        }
    }

    fun gpx(db: Db, out: OutputStream) = out.bufferedWriter().use { w ->
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Ghost\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n<trk><name>Ghost</name><trkseg>\n")
        db.forEachPoint { f ->
            w.write(String.format(Locale.US, "<trkpt lat=\"%.6f\" lon=\"%.6f\"><time>%s</time></trkpt>\n", f.lat, f.lon, Instant.ofEpochMilli(f.time)))
        }
        w.write("</trkseg></trk>\n</gpx>\n")
    }
}
