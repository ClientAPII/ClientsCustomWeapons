package de.clientapi.clientsCustomWeapons.config;

public enum Special {
    DISMOUNT_MOUNTED;

    public static Special parse(String s) {
        return Special.valueOf(s.trim().toUpperCase().replace('-', '_'));
    }
}