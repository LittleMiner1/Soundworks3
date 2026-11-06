# Soundworks - status

All features from the wish list are now implemented in code (blocks, network logic, screens, speaker audio, press status, creative tab, lang, README).

**Not compiled and not tested in-game.** The environment had no network and no Minecraft/Fabric jars, so nothing was built.
Run `./gradlew build` (or push to GitHub for the workflow) and report compiler errors.

Most likely trouble spots (26.3 API was written from the existing code and from memory):
`KeyEvent` in `keyPressed` (EditContainerScreen, ConfigScreen, NetScreen), `mouseScrolled(double,double,double,double)`,
`EditBox#setHint/canConsumeInput`, `FabricCreativeModeTab`, `TooltipDisplay`/`ItemLore`, `SimpleContainerData`, `Block#canSurvive`.

Known open issue: copying records in the press could not be reproduced by reading the code. The press now checks blank/source/result slot
on the client and shows the reason in the window. If copying still fails, send the server log.
