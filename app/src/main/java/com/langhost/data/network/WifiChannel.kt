package com.langhost.data.network

/** Converts a Wi-Fi center frequency in MHz to its IEEE channel number. */
object WifiChannel {
    fun fromFrequencyMhz(frequencyMhz: Int): Int = when {
        frequencyMhz in 2412..2472 && (frequencyMhz - 2412) % 5 == 0 ->
            (frequencyMhz - 2412) / 5 + 1
        frequencyMhz == 2484 -> 14
        frequencyMhz in 5000..5895 && frequencyMhz % 5 == 0 ->
            (frequencyMhz - 5000) / 5
        frequencyMhz == 5935 -> 2 // Special 6 GHz channel 2.
        frequencyMhz in 5955..7115 && (frequencyMhz - 5950) % 5 == 0 ->
            (frequencyMhz - 5950) / 5
        else -> 0
    }
}
