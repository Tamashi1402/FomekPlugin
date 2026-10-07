new Object() {
  public Direction getValue() {
    Direction _dir = Direction.NORTH;
    int _num = Mth.nextInt(RandomSource.create(), 1, 4);
    if (_num == 1) {
      _dir = Direction.EAST;
    } else if (_num == 2) {
      _dir = Direction.SOUTH;
    } else if (_num == 3) {
      _dir = Direction.WEST;
    }
    return _dir;
  }
}.getValue()
