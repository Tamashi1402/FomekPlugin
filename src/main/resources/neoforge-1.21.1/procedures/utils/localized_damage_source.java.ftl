private static DamageSource localizedDamageSource(DamageSource source, String trans) {
    return new DamageSource(source.typeHolder(), source.getEntity(), source.getDirectEntity()) {
        @Override public Component getLocalizedDeathMessage(LivingEntity msgEntity) {
            Component msgEntityName = msgEntity.getDisplayName();
            if (this.getEntity() == null && this.getDirectEntity() == null) {
                return msgEntity.getKillCredit() != null ?
                Component.translatable(trans + ".player", msgEntityName, msgEntity.getKillCredit().getDisplayName()) :
                Component.translatable(trans, msgEntityName);
            } else {
                Component component = this.getEntity() == null ? this.getDirectEntity().getDisplayName() : this.getEntity().getDisplayName();
                ItemStack heldItem = this.getEntity() instanceof LivingEntity _livingentity ? _livingentity.getMainHandItem() : ItemStack.EMPTY;
                return !heldItem.isEmpty() && heldItem.has(DataComponents.CUSTOM_NAME) ?
                Component.translatable(trans + ".item", msgEntityName, component, heldItem.getDisplayName()) :
                Component.translatable(trans, msgEntityName, component);
            }
        }
    };
}