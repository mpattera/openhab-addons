package org.openhab.binding.kseniasecurity.internal.model;

public class KseniaPartition {
    private final int id;

    private String description;

    private boolean armed;

    private boolean alarm;

    private boolean tampered;

    public KseniaPartition(int id) {
        this.id = id;
    }
}