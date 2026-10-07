private static ${JavaModName}TimedLoop createTimedLoop(int total, int ticks, Function<${JavaModName}TimedLoop, Boolean> forEach) {
    return new ${JavaModName}TimedLoop(total, ticks, forEach);
}