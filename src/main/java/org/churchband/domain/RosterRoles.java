package org.churchband.domain;

import java.util.List;

public final class RosterRoles {

    private RosterRoles() {
    }

    public static final List<Role> WEEKLY_ROLES = List.of(
            Role.WORSHIP_LEADER,
            Role.VOCALIST,
            Role.VOCALIST_2,
            Role.VOCALIST_3,
            Role.BASSIST,
            Role.DRUMMER,
            Role.KEYBOARDIST,
            Role.GUITARIST,
            Role.BAND_DIRECTOR,
            Role.SOUND,
            Role.LYRICS,
            Role.CAMERA
    );
}