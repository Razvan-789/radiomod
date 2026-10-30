package com.radiomod.network;

/** A receivable FM service or one DAB service within an ensemble. */
public record Dx3Station(boolean dab, int tuning, String sid, String mux, String label, String sortName,
                         String detail, int[] logoPixels) {
    public boolean sameStation(Dx3Station other) {
        if (other == null || dab != other.dab || !sid.equals(other.sid) || !mux.equals(other.mux)) {
            return false;
        }
        return tuning == other.tuning || !dab && !sid.isBlank();
    }
}
