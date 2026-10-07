private static int getPermissionLevel(Entity ent) {
    int lvl = 0;
    for (int Level = 0; Level < 4; Level++) {
        if (ent.hasPermissions(Level + 1)) {
            lvl++;
        } else {
            break;
        }
    }
    return lvl;
}