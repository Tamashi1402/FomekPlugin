private static class ${JavaModName}TimedLoop {
    private int iterator = 0;
    private int ticks = 0;
    private final int total;
    private final Function<${JavaModName}TimedLoop, Boolean> forEach;

    private ${JavaModName}TimedLoop(int total, int ticks, Function<${JavaModName}TimedLoop, Boolean> forEach) {
        this.total = total;
        this.ticks = ticks;
        this.forEach = forEach;
        run();
    }

	private void run() {
        if (forEach.apply(this))
            ${JavaModName}.queueServerWork(ticks, this::next);
	}

    private void next() {
        if (total > ++iterator)
            run();
    }
}