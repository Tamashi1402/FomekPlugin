<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onProjectileImpact(ProjectileImpactEvent event) {
		if (event != null && event.getEntity() != null) {

      double x = event.getEntity().getX();
      double y = event.getEntity().getY();
      double z = event.getEntity().getZ();
      
      double hx = event.getRayTraceResult().getLocation().x;
      double hy = event.getRayTraceResult().getLocation().y;
      double hz = event.getRayTraceResult().getLocation().z;
      
		  double bx = Math.floor(hx + 0.01 * Math.signum(hx - x));
		  double by = Math.floor(hy + 0.01 * Math.signum(hy - y));
		  double bz = Math.floor(hz + 0.01 * Math.signum(hz - z));
      
      LevelAccessor world = event.getEntity().level();
      
			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "x",
				"y": "y",
				"z": "z",
				"world": "world",
				"entity": "event.getEntity()",
				"sourceentity": "event.getProjectile().getOwner()",
				"hx": "hx",
				"hy": "hy",
				"hz": "hz",
				"hitresult": "event.getRayTraceResult().getType().toString().toLowerCase()",
				"bx": "bx",
				"by": "by",
				"bz": "bz",
				"hitblock": "world.getBlockState(BlockPos.containing(bx, by, bz))",
				"event": "event"

				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}