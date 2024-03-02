package de.fabmax.kool

import de.fabmax.kool.pipeline.backend.webgpu.GPUPowerPreference
import de.fabmax.kool.util.MsdfFontInfo
import de.fabmax.kool.util.MsdfMeta
import kotlinx.serialization.json.Json

data class KoolConfigJs(
    /**
     * Base path used by [Assets] to look for assets to be loaded (textures, models, etc.).
     */
    override val assetPath: String = "./assets",

    /**
     * Default font used by UI elements.
     */
    override val defaultFont: MsdfFontInfo = DEFAULT_MSDF_FONT_INFO,

    val canvasName: String = "glCanvas",
    val renderBackend: Backend = Backend.PREFER_WEB_GPU,
    val isGlobalKeyEventGrabbing: Boolean = true,
    val isJsCanvasToWindowFitting: Boolean = true,
    val powerPreference: GPUPowerPreference = GPUPowerPreference.highPerformance,
    val deviceScaleLimit: Double = 3.0,
    val forceFloatDepthBuffer: Boolean = true,
    val numSamples: Int = 4,
    val loaderTasks: List<suspend () -> Unit> = emptyList(),

    val customTtfFonts: Map<String, String> = emptyMap(),
) : KoolConfig {
    companion object {
        private const val DEFAULT_META_JSON = """
            {"atlas":{"type":"mtsdf","distanceRange":8,"size":36,"width":420,"height":420,"yOrigin":"bottom"},"name":"Roboto","metrics":{"emSize":1,"lineHeight":1.1718,"ascender":0.9277,"descender":-0.2441,"underlineY":-0.0976,"underlineThickness":0.0488},"glyphs":[{"unicode":32,"advance":0.2475},{"unicode":33,"advance":0.2573,"planeBounds":{"left":-0.0350,"bottom":-0.1194,"right":0.2982,"top":0.8250},"atlasBounds":{"left":0.5,"bottom":144.5,"right":12.5,"top":178.5}},{"unicode":34,"advance":0.3198,"planeBounds":{"left":-0.0554,"bottom":0.3932,"right":0.3889,"top":0.8655},"atlasBounds":{"left":403.5,"bottom":355.5,"right":419.5,"top":372.5}},{"unicode":35,"advance":0.6157,"planeBounds":{"left":-0.0583,"bottom":-0.1167,"right":0.7194,"top":0.8276},"atlasBounds":{"left":44.5,"bottom":144.5,"right":72.5,"top":178.5}},{"unicode":36,"advance":0.5615,"planeBounds":{"left":-0.0662,"bottom":-0.2210,"right":0.6282,"top":0.9456},"atlasBounds":{"left":222.5,"bottom":377.5,"right":247.5,"top":419.5}},{"unicode":37,"advance":0.7324,"planeBounds":{"left":-0.0604,"bottom":-0.1306,"right":0.8006,"top":0.8415},"atlasBounds":{"left":53.5,"bottom":216.5,"right":84.5,"top":251.5}},{"unicode":38,"advance":0.6215,"planeBounds":{"left":-0.0687,"bottom":-0.1306,"right":0.7367,"top":0.8415},"atlasBounds":{"left":85.5,"bottom":216.5,"right":114.5,"top":251.5}},{"unicode":39,"advance":0.1743,"planeBounds":{"left":-0.0658,"bottom":0.3969,"right":0.2396,"top":0.8691},"atlasBounds":{"left":407.5,"bottom":56.5,"right":418.5,"top":73.5}},{"unicode":40,"advance":0.3417,"planeBounds":{"left":-0.0561,"bottom":-0.3525,"right":0.4438,"top":0.9252},"atlasBounds":{"left":0.5,"bottom":373.5,"right":18.5,"top":419.5}},{"unicode":41,"advance":0.3476,"planeBounds":{"left":-0.1022,"bottom":-0.3525,"right":0.3977,"top":0.9252},"atlasBounds":{"left":19.5,"bottom":373.5,"right":37.5,"top":419.5}},{"unicode":42,"advance":0.4306,"planeBounds":{"left":-0.1043,"bottom":0.1847,"right":0.5345,"top":0.8235},"atlasBounds":{"left":185.5,"bottom":18.5,"right":208.5,"top":41.5}},{"unicode":43,"advance":0.5668,"planeBounds":{"left":-0.0793,"bottom":-0.0449,"right":0.6428,"top":0.7050},"atlasBounds":{"left":41.5,"bottom":14.5,"right":67.5,"top":41.5}},{"unicode":44,"advance":0.1962,"planeBounds":{"left":-0.0982,"bottom":-0.2534,"right":0.2628,"top":0.2187},"atlasBounds":{"left":339.5,"bottom":24.5,"right":352.5,"top":41.5}},{"unicode":45,"advance":0.2758,"planeBounds":{"left":-0.0989,"bottom":0.1492,"right":0.3733,"top":0.4547},"atlasBounds":{"left":400.5,"bottom":179.5,"right":417.5,"top":190.5}},{"unicode":46,"advance":0.2631,"planeBounds":{"left":-0.0540,"bottom":-0.1183,"right":0.3070,"top":0.2150},"atlasBounds":{"left":21.5,"bottom":0.5,"right":34.5,"top":12.5}},{"unicode":47,"advance":0.4121,"planeBounds":{"left":-0.1097,"bottom":-0.1750,"right":0.5013,"top":0.8249},"atlasBounds":{"left":386.5,"bottom":294.5,"right":408.5,"top":330.5}},{"unicode":48,"advance":0.5615,"planeBounds":{"left":-0.0667,"bottom":-0.1306,"right":0.6277,"top":0.8415},"atlasBounds":{"left":139.5,"bottom":216.5,"right":164.5,"top":251.5}},{"unicode":49,"advance":0.5615,"planeBounds":{"left":-0.0305,"bottom":-0.1150,"right":0.4694,"top":0.8293},"atlasBounds":{"left":401.5,"bottom":254.5,"right":419.5,"top":288.5}},{"unicode":50,"advance":0.5615,"planeBounds":{"left":-0.0759,"bottom":-0.1118,"right":0.6462,"top":0.8325},"atlasBounds":{"left":162.5,"bottom":144.5,"right":188.5,"top":178.5}},{"unicode":51,"advance":0.5615,"planeBounds":{"left":-0.0759,"bottom":-0.1306,"right":0.6184,"top":0.8415},"atlasBounds":{"left":165.5,"bottom":216.5,"right":190.5,"top":251.5}},{"unicode":52,"advance":0.5615,"planeBounds":{"left":-0.0925,"bottom":-0.1167,"right":0.6574,"top":0.8276},"atlasBounds":{"left":212.5,"bottom":144.5,"right":239.5,"top":178.5}},{"unicode":53,"advance":0.5615,"planeBounds":{"left":-0.0486,"bottom":-0.1216,"right":0.6458,"top":0.8228},"atlasBounds":{"left":240.5,"bottom":144.5,"right":265.5,"top":178.5}},{"unicode":54,"advance":0.5615,"planeBounds":{"left":-0.0581,"bottom":-0.1213,"right":0.6362,"top":0.8230},"atlasBounds":{"left":266.5,"bottom":144.5,"right":291.5,"top":178.5}},{"unicode":55,"advance":0.5615,"planeBounds":{"left":-0.0832,"bottom":-0.1167,"right":0.6389,"top":0.8276},"atlasBounds":{"left":292.5,"bottom":144.5,"right":318.5,"top":178.5}},{"unicode":56,"advance":0.5615,"planeBounds":{"left":-0.0664,"bottom":-0.1306,"right":0.6279,"top":0.8415},"atlasBounds":{"left":191.5,"bottom":216.5,"right":216.5,"top":251.5}},{"unicode":57,"advance":0.5615,"planeBounds":{"left":-0.0747,"bottom":-0.1121,"right":0.6196,"top":0.8323},"atlasBounds":{"left":319.5,"bottom":144.5,"right":344.5,"top":178.5}},{"unicode":58,"advance":0.2421,"planeBounds":{"left":-0.0587,"bottom":-0.1249,"right":0.3023,"top":0.6528},"atlasBounds":{"left":0.5,"bottom":13.5,"right":13.5,"top":41.5}},{"unicode":59,"advance":0.2114,"planeBounds":{"left":-0.1011,"bottom":-0.2625,"right":0.2877,"top":0.6541},"atlasBounds":{"left":302.5,"bottom":75.5,"right":316.5,"top":108.5}},{"unicode":60,"advance":0.5083,"planeBounds":{"left":-0.0845,"bottom":-0.0176,"right":0.5543,"top":0.6490},"atlasBounds":{"left":118.5,"bottom":17.5,"right":141.5,"top":41.5}},{"unicode":61,"advance":0.5488,"planeBounds":{"left":-0.0416,"bottom":0.0715,"right":0.5972,"top":0.5993},"atlasBounds":{"left":297.5,"bottom":22.5,"right":320.5,"top":41.5}},{"unicode":62,"advance":0.5224,"planeBounds":{"left":-0.0594,"bottom":-0.0171,"right":0.6072,"top":0.6494},"atlasBounds":{"left":93.5,"bottom":17.5,"right":117.5,"top":41.5}},{"unicode":63,"advance":0.4721,"planeBounds":{"left":-0.0848,"bottom":-0.1284,"right":0.5540,"top":0.8437},"atlasBounds":{"left":241.5,"bottom":216.5,"right":264.5,"top":251.5}},{"unicode":64,"advance":0.8979,"planeBounds":{"left":-0.0607,"bottom":-0.3445,"right":0.9670,"top":0.8221},"atlasBounds":{"left":274.5,"bottom":377.5,"right":311.5,"top":419.5}},{"unicode":65,"advance":0.6523,"planeBounds":{"left":-0.1041,"bottom":-0.1167,"right":0.7569,"top":0.8276},"atlasBounds":{"left":345.5,"bottom":144.5,"right":376.5,"top":178.5}},{"unicode":66,"advance":0.6225,"planeBounds":{"left":-0.0366,"bottom":-0.1167,"right":0.6855,"top":0.8276},"atlasBounds":{"left":54.5,"bottom":109.5,"right":80.5,"top":143.5}},{"unicode":67,"advance":0.6508,"planeBounds":{"left":-0.0571,"bottom":-0.1306,"right":0.7206,"top":0.8415},"atlasBounds":{"left":291.5,"bottom":216.5,"right":319.5,"top":251.5}},{"unicode":68,"advance":0.6557,"planeBounds":{"left":-0.0354,"bottom":-0.1167,"right":0.7145,"top":0.8276},"atlasBounds":{"left":109.5,"bottom":109.5,"right":136.5,"top":143.5}},{"unicode":69,"advance":0.5683,"planeBounds":{"left":-0.0388,"bottom":-0.1167,"right":0.6555,"top":0.8276},"atlasBounds":{"left":137.5,"bottom":109.5,"right":162.5,"top":143.5}},{"unicode":70,"advance":0.5527,"planeBounds":{"left":-0.0305,"bottom":-0.1167,"right":0.6360,"top":0.8276},"atlasBounds":{"left":163.5,"bottom":109.5,"right":187.5,"top":143.5}},{"unicode":71,"advance":0.6811,"planeBounds":{"left":-0.0553,"bottom":-0.1306,"right":0.7223,"top":0.8415},"atlasBounds":{"left":320.5,"bottom":216.5,"right":348.5,"top":251.5}},{"unicode":72,"advance":0.7128,"planeBounds":{"left":-0.0331,"bottom":-0.1167,"right":0.7446,"top":0.8276},"atlasBounds":{"left":188.5,"bottom":109.5,"right":216.5,"top":143.5}},{"unicode":73,"advance":0.2719,"planeBounds":{"left":-0.0304,"bottom":-0.1167,"right":0.3028,"top":0.8276},"atlasBounds":{"left":217.5,"bottom":109.5,"right":229.5,"top":143.5}},{"unicode":74,"advance":0.5517,"planeBounds":{"left":-0.0969,"bottom":-0.1216,"right":0.5974,"top":0.8228},"atlasBounds":{"left":256.5,"bottom":109.5,"right":281.5,"top":143.5}},{"unicode":75,"advance":0.6269,"planeBounds":{"left":-0.0339,"bottom":-0.1167,"right":0.7438,"top":0.8276},"atlasBounds":{"left":282.5,"bottom":109.5,"right":310.5,"top":143.5}},{"unicode":76,"advance":0.5380,"planeBounds":{"left":-0.0352,"bottom":-0.1167,"right":0.6314,"top":0.8276},"atlasBounds":{"left":311.5,"bottom":109.5,"right":335.5,"top":143.5}},{"unicode":77,"advance":0.8730,"planeBounds":{"left":-0.0359,"bottom":-0.1167,"right":0.9085,"top":0.8276},"atlasBounds":{"left":369.5,"bottom":109.5,"right":403.5,"top":143.5}},{"unicode":78,"advance":0.7128,"planeBounds":{"left":-0.0331,"bottom":-0.1167,"right":0.7446,"top":0.8276},"atlasBounds":{"left":245.5,"bottom":74.5,"right":273.5,"top":108.5}},{"unicode":79,"advance":0.6875,"planeBounds":{"left":-0.0592,"bottom":-0.1306,"right":0.7462,"top":0.8415},"atlasBounds":{"left":349.5,"bottom":216.5,"right":378.5,"top":251.5}},{"unicode":80,"advance":0.6308,"planeBounds":{"left":-0.0368,"bottom":-0.1167,"right":0.7131,"top":0.8276},"atlasBounds":{"left":274.5,"bottom":74.5,"right":301.5,"top":108.5}},{"unicode":81,"advance":0.6875,"planeBounds":{"left":-0.0622,"bottom":-0.2413,"right":0.7433,"top":0.8419},"atlasBounds":{"left":196.5,"bottom":291.5,"right":225.5,"top":330.5}},{"unicode":82,"advance":0.6157,"planeBounds":{"left":-0.0349,"bottom":-0.1167,"right":0.7150,"top":0.8276},"atlasBounds":{"left":217.5,"bottom":74.5,"right":244.5,"top":108.5}},{"unicode":83,"advance":0.5932,"planeBounds":{"left":-0.0776,"bottom":-0.1306,"right":0.6723,"top":0.8415},"atlasBounds":{"left":379.5,"bottom":216.5,"right":406.5,"top":251.5}},{"unicode":84,"advance":0.5966,"planeBounds":{"left":-0.0900,"bottom":-0.1167,"right":0.6877,"top":0.8276},"atlasBounds":{"left":160.5,"bottom":74.5,"right":188.5,"top":108.5}},{"unicode":85,"advance":0.6484,"planeBounds":{"left":-0.0493,"bottom":-0.1216,"right":0.7006,"top":0.8228},"atlasBounds":{"left":132.5,"bottom":74.5,"right":159.5,"top":108.5}},{"unicode":86,"advance":0.6362,"planeBounds":{"left":-0.0980,"bottom":-0.1167,"right":0.7352,"top":0.8276},"atlasBounds":{"left":101.5,"bottom":74.5,"right":131.5,"top":108.5}},{"unicode":87,"advance":0.8872,"planeBounds":{"left":-0.0939,"bottom":-0.1167,"right":0.9894,"top":0.8276},"atlasBounds":{"left":61.5,"bottom":74.5,"right":100.5,"top":108.5}},{"unicode":88,"advance":0.6269,"planeBounds":{"left":-0.0885,"bottom":-0.1167,"right":0.7169,"top":0.8276},"atlasBounds":{"left":31.5,"bottom":74.5,"right":60.5,"top":108.5}},{"unicode":89,"advance":0.6005,"planeBounds":{"left":-0.1173,"bottom":-0.1167,"right":0.7159,"top":0.8276},"atlasBounds":{"left":0.5,"bottom":74.5,"right":30.5,"top":108.5}},{"unicode":90,"advance":0.5986,"planeBounds":{"left":-0.0742,"bottom":-0.1167,"right":0.6757,"top":0.8276},"atlasBounds":{"left":189.5,"bottom":74.5,"right":216.5,"top":108.5}},{"unicode":91,"advance":0.2651,"planeBounds":{"left":-0.0450,"bottom":-0.2671,"right":0.3716,"top":0.9273},"atlasBounds":{"left":90.5,"bottom":376.5,"right":105.5,"top":419.5}},{"unicode":92,"advance":0.4101,"planeBounds":{"left":-0.0946,"bottom":-0.1750,"right":0.5164,"top":0.8249},"atlasBounds":{"left":180.5,"bottom":252.5,"right":202.5,"top":288.5}},{"unicode":93,"advance":0.2651,"planeBounds":{"left":-0.1116,"bottom":-0.2671,"right":0.3050,"top":0.9273},"atlasBounds":{"left":106.5,"bottom":376.5,"right":121.5,"top":419.5}},{"unicode":94,"advance":0.4179,"planeBounds":{"left":-0.0836,"bottom":0.2417,"right":0.4996,"top":0.8251},"atlasBounds":{"left":275.5,"bottom":20.5,"right":296.5,"top":41.5}},{"unicode":95,"advance":0.4511,"planeBounds":{"left":-0.1216,"bottom":-0.1896,"right":0.5728,"top":0.1159},"atlasBounds":{"left":49.5,"bottom":1.5,"right":74.5,"top":12.5}},{"unicode":96,"advance":0.3090,"planeBounds":{"left":-0.0925,"bottom":0.4837,"right":0.3518,"top":0.8726},"atlasBounds":{"left":403.5,"bottom":340.5,"right":419.5,"top":354.5}},{"unicode":97,"advance":0.5439,"planeBounds":{"left":-0.0620,"bottom":-0.1247,"right":0.6045,"top":0.6530},"atlasBounds":{"left":163.5,"bottom":45.5,"right":187.5,"top":73.5}},{"unicode":98,"advance":0.5610,"planeBounds":{"left":-0.0552,"bottom":-0.1298,"right":0.6392,"top":0.8701},"atlasBounds":{"left":227.5,"bottom":252.5,"right":252.5,"top":288.5}},{"unicode":99,"advance":0.5234,"planeBounds":{"left":-0.0796,"bottom":-0.1247,"right":0.6148,"top":0.6530},"atlasBounds":{"left":113.5,"bottom":45.5,"right":138.5,"top":73.5}},{"unicode":100,"advance":0.5639,"planeBounds":{"left":-0.0779,"bottom":-0.1298,"right":0.6165,"top":0.8701},"atlasBounds":{"left":278.5,"bottom":252.5,"right":303.5,"top":288.5}},{"unicode":101,"advance":0.5297,"planeBounds":{"left":-0.0776,"bottom":-0.1247,"right":0.6167,"top":0.6530},"atlasBounds":{"left":188.5,"bottom":45.5,"right":213.5,"top":73.5}},{"unicode":102,"advance":0.3471,"planeBounds":{"left":-0.0888,"bottom":-0.1198,"right":0.4667,"top":0.8801},"atlasBounds":{"left":304.5,"bottom":252.5,"right":324.5,"top":288.5}},{"unicode":103,"advance":0.5610,"planeBounds":{"left":-0.0772,"bottom":-0.3210,"right":0.6172,"top":0.6511},"atlasBounds":{"left":198.5,"bottom":179.5,"right":223.5,"top":214.5}},{"unicode":104,"advance":0.5507,"planeBounds":{"left":-0.0433,"bottom":-0.1111,"right":0.5955,"top":0.8611},"atlasBounds":{"left":224.5,"bottom":179.5,"right":247.5,"top":214.5}},{"unicode":105,"advance":0.2426,"planeBounds":{"left":-0.0443,"bottom":-0.1118,"right":0.2889,"top":0.8325},"atlasBounds":{"left":13.5,"bottom":109.5,"right":25.5,"top":143.5}},{"unicode":106,"advance":0.2387,"planeBounds":{"left":-0.1538,"bottom":-0.3296,"right":0.2905,"top":0.8369},"atlasBounds":{"left":312.5,"bottom":377.5,"right":328.5,"top":419.5}},{"unicode":107,"advance":0.5068,"planeBounds":{"left":-0.0459,"bottom":-0.1111,"right":0.6206,"top":0.8611},"atlasBounds":{"left":273.5,"bottom":179.5,"right":297.5,"top":214.5}},{"unicode":108,"advance":0.2426,"planeBounds":{"left":-0.0453,"bottom":-0.1111,"right":0.2880,"top":0.8611},"atlasBounds":{"left":407.5,"bottom":216.5,"right":419.5,"top":251.5}},{"unicode":109,"advance":0.8764,"planeBounds":{"left":-0.0478,"bottom":-0.1198,"right":0.9243,"top":0.6579},"atlasBounds":{"left":371.5,"bottom":45.5,"right":406.5,"top":73.5}},{"unicode":110,"advance":0.5517,"planeBounds":{"left":-0.0433,"bottom":-0.1198,"right":0.5955,"top":0.6579},"atlasBounds":{"left":347.5,"bottom":45.5,"right":370.5,"top":73.5}},{"unicode":111,"advance":0.5703,"planeBounds":{"left":-0.0761,"bottom":-0.1247,"right":0.6460,"top":0.6530},"atlasBounds":{"left":320.5,"bottom":45.5,"right":346.5,"top":73.5}},{"unicode":112,"advance":0.5610,"planeBounds":{"left":-0.0557,"bottom":-0.3186,"right":0.6387,"top":0.6535},"atlasBounds":{"left":298.5,"bottom":179.5,"right":323.5,"top":214.5}},{"unicode":113,"advance":0.5683,"planeBounds":{"left":-0.0781,"bottom":-0.3186,"right":0.6162,"top":0.6535},"atlasBounds":{"left":324.5,"bottom":179.5,"right":349.5,"top":214.5}},{"unicode":114,"advance":0.3383,"planeBounds":{"left":-0.0539,"bottom":-0.1198,"right":0.4460,"top":0.6579},"atlasBounds":{"left":301.5,"bottom":45.5,"right":319.5,"top":73.5}},{"unicode":115,"advance":0.5156,"planeBounds":{"left":-0.0769,"bottom":-0.1247,"right":0.5896,"top":0.6530},"atlasBounds":{"left":251.5,"bottom":45.5,"right":275.5,"top":73.5}},{"unicode":116,"advance":0.3266,"planeBounds":{"left":-0.1156,"bottom":-0.1212,"right":0.4120,"top":0.7676},"atlasBounds":{"left":317.5,"bottom":76.5,"right":336.5,"top":108.5}},{"unicode":117,"advance":0.5512,"planeBounds":{"left":-0.0450,"bottom":-0.1296,"right":0.5938,"top":0.6481},"atlasBounds":{"left":139.5,"bottom":45.5,"right":162.5,"top":73.5}},{"unicode":118,"advance":0.4843,"planeBounds":{"left":-0.1062,"bottom":-0.1247,"right":0.5881,"top":0.6530},"atlasBounds":{"left":87.5,"bottom":45.5,"right":112.5,"top":73.5}},{"unicode":119,"advance":0.7514,"planeBounds":{"left":-0.0977,"bottom":-0.1247,"right":0.8467,"top":0.6530},"atlasBounds":{"left":52.5,"bottom":45.5,"right":86.5,"top":73.5}},{"unicode":120,"advance":0.4956,"planeBounds":{"left":-0.1003,"bottom":-0.1247,"right":0.5940,"top":0.6530},"atlasBounds":{"left":26.5,"bottom":45.5,"right":51.5,"top":73.5}},{"unicode":121,"advance":0.4731,"planeBounds":{"left":-0.1113,"bottom":-0.3286,"right":0.5830,"top":0.6435},"atlasBounds":{"left":350.5,"bottom":179.5,"right":375.5,"top":214.5}},{"unicode":122,"advance":0.4956,"planeBounds":{"left":-0.0806,"bottom":-0.1247,"right":0.5860,"top":0.6530},"atlasBounds":{"left":276.5,"bottom":45.5,"right":300.5,"top":73.5}},{"unicode":123,"advance":0.3383,"planeBounds":{"left":-0.0846,"bottom":-0.2966,"right":0.4430,"top":0.8977},"atlasBounds":{"left":122.5,"bottom":376.5,"right":141.5,"top":419.5}},{"unicode":124,"advance":0.2436,"planeBounds":{"left":-0.0309,"bottom":-0.2521,"right":0.2746,"top":0.8312},"atlasBounds":{"left":226.5,"bottom":291.5,"right":237.5,"top":330.5}},{"unicode":125,"advance":0.3383,"planeBounds":{"left":-0.1064,"bottom":-0.2966,"right":0.4213,"top":0.8977},"atlasBounds":{"left":142.5,"bottom":376.5,"right":161.5,"top":419.5}},{"unicode":126,"advance":0.6801,"planeBounds":{"left":-0.0485,"bottom":0.0717,"right":0.7292,"top":0.5161},"atlasBounds":{"left":353.5,"bottom":25.5,"right":381.5,"top":41.5}},{"unicode":160,"advance":0.2475},{"unicode":161,"advance":0.2436,"planeBounds":{"left":-0.0453,"bottom":-0.2913,"right":0.2880,"top":0.6531},"atlasBounds":{"left":0.5,"bottom":109.5,"right":12.5,"top":143.5}},{"unicode":162,"advance":0.5468,"planeBounds":{"left":-0.0732,"bottom":-0.2380,"right":0.6211,"top":0.7619},"atlasBounds":{"left":0.5,"bottom":215.5,"right":25.5,"top":251.5}},{"unicode":163,"advance":0.5810,"planeBounds":{"left":-0.0773,"bottom":-0.1118,"right":0.6726,"top":0.8325},"atlasBounds":{"left":26.5,"bottom":109.5,"right":53.5,"top":143.5}},{"unicode":164,"advance":0.7128,"planeBounds":{"left":-0.0702,"bottom":-0.1283,"right":0.7909,"top":0.7328},"atlasBounds":{"left":364.5,"bottom":77.5,"right":395.5,"top":108.5}},{"unicode":165,"advance":0.5249,"planeBounds":{"left":-0.1125,"bottom":-0.1167,"right":0.6374,"top":0.8276},"atlasBounds":{"left":81.5,"bottom":109.5,"right":108.5,"top":143.5}},{"unicode":166,"advance":0.2397,"planeBounds":{"left":-0.0494,"bottom":-0.2521,"right":0.2838,"top":0.8312},"atlasBounds":{"left":238.5,"bottom":291.5,"right":250.5,"top":330.5}},{"unicode":167,"advance":0.6132,"planeBounds":{"left":-0.0734,"bottom":-0.3577,"right":0.6765,"top":0.8367},"atlasBounds":{"left":162.5,"bottom":376.5,"right":189.5,"top":419.5}},{"unicode":168,"advance":0.4179,"planeBounds":{"left":-0.0700,"bottom":0.5025,"right":0.4855,"top":0.8358},"atlasBounds":{"left":0.5,"bottom":0.5,"right":20.5,"top":12.5}},{"unicode":169,"advance":0.7856,"planeBounds":{"left":-0.0674,"bottom":-0.1308,"right":0.8492,"top":0.8413},"atlasBounds":{"left":102.5,"bottom":179.5,"right":135.5,"top":214.5}},{"unicode":170,"advance":0.4467,"planeBounds":{"left":-0.0507,"bottom":0.2234,"right":0.5048,"top":0.8346},"atlasBounds":{"left":254.5,"bottom":19.5,"right":274.5,"top":41.5}},{"unicode":171,"advance":0.4692,"planeBounds":{"left":-0.0687,"bottom":-0.0374,"right":0.5423,"top":0.5736},"atlasBounds":{"left":231.5,"bottom":19.5,"right":253.5,"top":41.5}},{"unicode":172,"advance":0.5537,"planeBounds":{"left":-0.0545,"bottom":0.0646,"right":0.5843,"top":0.5090},"atlasBounds":{"left":396.5,"bottom":92.5,"right":419.5,"top":108.5}},{"unicode":173,"advance":0.2758,"planeBounds":{"left":-0.0989,"bottom":0.1492,"right":0.3733,"top":0.4547},"atlasBounds":{"left":75.5,"bottom":1.5,"right":92.5,"top":12.5}},{"unicode":174,"advance":0.7861,"planeBounds":{"left":-0.0679,"bottom":-0.1308,"right":0.8487,"top":0.8413},"atlasBounds":{"left":0.5,"bottom":179.5,"right":33.5,"top":214.5}},{"unicode":175,"advance":0.4580,"planeBounds":{"left":-0.0443,"bottom":0.5178,"right":0.5111,"top":0.8234},"atlasBounds":{"left":93.5,"bottom":1.5,"right":113.5,"top":12.5}},{"unicode":176,"advance":0.3735,"planeBounds":{"left":-0.0490,"bottom":0.3447,"right":0.4231,"top":0.8447},"atlasBounds":{"left":321.5,"bottom":23.5,"right":338.5,"top":41.5}},{"unicode":177,"advance":0.5341,"planeBounds":{"left":-0.0762,"bottom":-0.1212,"right":0.6182,"top":0.7398},"atlasBounds":{"left":0.5,"bottom":42.5,"right":25.5,"top":73.5}},{"unicode":178,"advance":0.3666,"planeBounds":{"left":-0.0810,"bottom":0.2015,"right":0.4467,"top":0.8404},"atlasBounds":{"left":165.5,"bottom":18.5,"right":184.5,"top":41.5}},{"unicode":179,"advance":0.3666,"planeBounds":{"left":-0.0861,"bottom":0.1988,"right":0.4416,"top":0.8377},"atlasBounds":{"left":400.5,"bottom":191.5,"right":419.5,"top":214.5}},{"unicode":180,"advance":0.3134,"planeBounds":{"left":-0.0603,"bottom":0.4837,"right":0.3840,"top":0.8726},"atlasBounds":{"left":396.5,"bottom":77.5,"right":412.5,"top":91.5}},{"unicode":181,"advance":0.5664,"planeBounds":{"left":-0.0362,"bottom":-0.3235,"right":0.6026,"top":0.6487},"atlasBounds":{"left":217.5,"bottom":216.5,"right":240.5,"top":251.5}},{"unicode":182,"advance":0.4887,"planeBounds":{"left":-0.0860,"bottom":-0.1167,"right":0.5250,"top":0.8276},"atlasBounds":{"left":189.5,"bottom":144.5,"right":211.5,"top":178.5}},{"unicode":183,"advance":0.2607,"planeBounds":{"left":-0.0526,"bottom":0.1897,"right":0.3084,"top":0.5231},"atlasBounds":{"left":35.5,"bottom":0.5,"right":48.5,"top":12.5}},{"unicode":184,"advance":0.2475,"planeBounds":{"left":-0.0621,"bottom":-0.3284,"right":0.3267,"top":0.1160},"atlasBounds":{"left":382.5,"bottom":25.5,"right":396.5,"top":41.5}},{"unicode":185,"advance":0.3666,"planeBounds":{"left":-0.0576,"bottom":0.2127,"right":0.3589,"top":0.8238},"atlasBounds":{"left":404.5,"bottom":121.5,"right":419.5,"top":143.5}},{"unicode":186,"advance":0.4545,"planeBounds":{"left":-0.0648,"bottom":0.2232,"right":0.5184,"top":0.8343},"atlasBounds":{"left":209.5,"bottom":19.5,"right":230.5,"top":41.5}},{"unicode":187,"advance":0.4687,"planeBounds":{"left":-0.0638,"bottom":-0.0506,"right":0.5472,"top":0.5882},"atlasBounds":{"left":142.5,"bottom":18.5,"right":164.5,"top":41.5}},{"unicode":188,"advance":0.7324,"planeBounds":{"left":-0.0757,"bottom":-0.1174,"right":0.8130,"top":0.8269},"atlasBounds":{"left":336.5,"bottom":109.5,"right":368.5,"top":143.5}},{"unicode":189,"advance":0.7758,"planeBounds":{"left":-0.0772,"bottom":-0.1174,"right":0.8394,"top":0.8269},"atlasBounds":{"left":128.5,"bottom":144.5,"right":161.5,"top":178.5}},{"unicode":190,"advance":0.7778,"planeBounds":{"left":-0.0608,"bottom":-0.1140,"right":0.8557,"top":0.8303},"atlasBounds":{"left":94.5,"bottom":144.5,"right":127.5,"top":178.5}},{"unicode":191,"advance":0.4731,"planeBounds":{"left":-0.0860,"bottom":-0.3113,"right":0.5528,"top":0.6609},"atlasBounds":{"left":376.5,"bottom":179.5,"right":399.5,"top":214.5}},{"unicode":192,"advance":0.6523,"planeBounds":{"left":-0.1041,"bottom":-0.1187,"right":0.7569,"top":1.0201},"atlasBounds":{"left":358.5,"bottom":378.5,"right":389.5,"top":419.5}},{"unicode":193,"advance":0.6523,"planeBounds":{"left":-0.1041,"bottom":-0.1187,"right":0.7569,"top":1.0201},"atlasBounds":{"left":0.5,"bottom":331.5,"right":31.5,"top":372.5}},{"unicode":194,"advance":0.6523,"planeBounds":{"left":-0.1041,"bottom":-0.1187,"right":0.7569,"top":1.0201},"atlasBounds":{"left":32.5,"bottom":331.5,"right":63.5,"top":372.5}},{"unicode":195,"advance":0.6523,"planeBounds":{"left":-0.1041,"bottom":-0.1236,"right":0.7569,"top":1.0152},"atlasBounds":{"left":64.5,"bottom":331.5,"right":95.5,"top":372.5}},{"unicode":196,"advance":0.6523,"planeBounds":{"left":-0.1041,"bottom":-0.1192,"right":0.7569,"top":0.9918},"atlasBounds":{"left":59.5,"bottom":290.5,"right":90.5,"top":330.5}},{"unicode":197,"advance":0.6523,"planeBounds":{"left":-0.1041,"bottom":-0.1243,"right":0.7569,"top":1.0701},"atlasBounds":{"left":190.5,"bottom":376.5,"right":221.5,"top":419.5}},{"unicode":198,"advance":0.9345,"planeBounds":{"left":-0.1280,"bottom":-0.1167,"right":1.0386,"top":0.8276},"atlasBounds":{"left":377.5,"bottom":144.5,"right":419.5,"top":178.5}},{"unicode":199,"advance":0.6508,"planeBounds":{"left":-0.0571,"bottom":-0.3313,"right":0.7206,"top":0.8352},"atlasBounds":{"left":329.5,"bottom":377.5,"right":357.5,"top":419.5}},{"unicode":200,"advance":0.5683,"planeBounds":{"left":-0.0388,"bottom":-0.1158,"right":0.6555,"top":1.0230},"atlasBounds":{"left":96.5,"bottom":331.5,"right":121.5,"top":372.5}},{"unicode":201,"advance":0.5683,"planeBounds":{"left":-0.0388,"bottom":-0.1158,"right":0.6555,"top":1.0230},"atlasBounds":{"left":122.5,"bottom":331.5,"right":147.5,"top":372.5}},{"unicode":202,"advance":0.5683,"planeBounds":{"left":-0.0388,"bottom":-0.1158,"right":0.6555,"top":1.0230},"atlasBounds":{"left":148.5,"bottom":331.5,"right":173.5,"top":372.5}},{"unicode":203,"advance":0.5683,"planeBounds":{"left":-0.0388,"bottom":-0.1163,"right":0.6555,"top":0.9947},"atlasBounds":{"left":119.5,"bottom":290.5,"right":144.5,"top":330.5}},{"unicode":204,"advance":0.2719,"planeBounds":{"left":-0.1360,"bottom":-0.1158,"right":0.3084,"top":1.0230},"atlasBounds":{"left":174.5,"bottom":331.5,"right":190.5,"top":372.5}},{"unicode":205,"advance":0.2719,"planeBounds":{"left":-0.0344,"bottom":-0.1158,"right":0.4099,"top":1.0230},"atlasBounds":{"left":191.5,"bottom":331.5,"right":207.5,"top":372.5}},{"unicode":206,"advance":0.2719,"planeBounds":{"left":-0.1274,"bottom":-0.1158,"right":0.4003,"top":1.0230},"atlasBounds":{"left":208.5,"bottom":331.5,"right":227.5,"top":372.5}},{"unicode":207,"advance":0.2719,"planeBounds":{"left":-0.1403,"bottom":-0.1163,"right":0.4152,"top":0.9947},"atlasBounds":{"left":145.5,"bottom":290.5,"right":165.5,"top":330.5}},{"unicode":208,"advance":0.6704,"planeBounds":{"left":-0.1092,"bottom":-0.1167,"right":0.7240,"top":0.8276},"atlasBounds":{"left":13.5,"bottom":144.5,"right":43.5,"top":178.5}},{"unicode":209,"advance":0.7128,"planeBounds":{"left":-0.0331,"bottom":-0.1236,"right":0.7446,"top":1.0152},"atlasBounds":{"left":228.5,"bottom":331.5,"right":256.5,"top":372.5}},{"unicode":210,"advance":0.6875,"planeBounds":{"left":-0.0592,"bottom":-0.1231,"right":0.7462,"top":1.0157},"atlasBounds":{"left":390.5,"bottom":378.5,"right":419.5,"top":419.5}},{"unicode":211,"advance":0.6875,"planeBounds":{"left":-0.0592,"bottom":-0.1231,"right":0.7462,"top":1.0157},"atlasBounds":{"left":257.5,"bottom":331.5,"right":286.5,"top":372.5}},{"unicode":212,"advance":0.6875,"planeBounds":{"left":-0.0592,"bottom":-0.1231,"right":0.7462,"top":1.0157},"atlasBounds":{"left":287.5,"bottom":331.5,"right":316.5,"top":372.5}},{"unicode":213,"advance":0.6875,"planeBounds":{"left":-0.0592,"bottom":-0.1280,"right":0.7462,"top":1.0108},"atlasBounds":{"left":317.5,"bottom":331.5,"right":346.5,"top":372.5}},{"unicode":214,"advance":0.6875,"planeBounds":{"left":-0.0592,"bottom":-0.1236,"right":0.7462,"top":0.9874},"atlasBounds":{"left":166.5,"bottom":290.5,"right":195.5,"top":330.5}},{"unicode":215,"advance":0.5332,"planeBounds":{"left":-0.0701,"bottom":-0.0227,"right":0.5965,"top":0.6716},"atlasBounds":{"left":68.5,"bottom":16.5,"right":92.5,"top":41.5}},{"unicode":216,"advance":0.6875,"planeBounds":{"left":-0.0543,"bottom":-0.1664,"right":0.7511,"top":0.8613},"atlasBounds":{"left":277.5,"bottom":293.5,"right":306.5,"top":330.5}},{"unicode":217,"advance":0.6484,"planeBounds":{"left":-0.0493,"bottom":-0.1236,"right":0.7006,"top":1.0152},"atlasBounds":{"left":347.5,"bottom":331.5,"right":374.5,"top":372.5}},{"unicode":218,"advance":0.6484,"planeBounds":{"left":-0.0493,"bottom":-0.1236,"right":0.7006,"top":1.0152},"atlasBounds":{"left":375.5,"bottom":331.5,"right":402.5,"top":372.5}},{"unicode":219,"advance":0.6484,"planeBounds":{"left":-0.0493,"bottom":-0.1236,"right":0.7006,"top":1.0152},"atlasBounds":{"left":0.5,"bottom":289.5,"right":27.5,"top":330.5}},{"unicode":220,"advance":0.6484,"planeBounds":{"left":-0.0493,"bottom":-0.1241,"right":0.7006,"top":0.9869},"atlasBounds":{"left":91.5,"bottom":290.5,"right":118.5,"top":330.5}},{"unicode":221,"advance":0.6005,"planeBounds":{"left":-0.1173,"bottom":-0.1187,"right":0.7159,"top":1.0201},"atlasBounds":{"left":28.5,"bottom":289.5,"right":58.5,"top":330.5}},{"unicode":222,"advance":0.5908,"planeBounds":{"left":-0.0339,"bottom":-0.1167,"right":0.6604,"top":0.8276},"atlasBounds":{"left":230.5,"bottom":109.5,"right":255.5,"top":143.5}},{"unicode":223,"advance":0.5947,"planeBounds":{"left":-0.0512,"bottom":-0.1254,"right":0.6709,"top":0.8745},"atlasBounds":{"left":332.5,"bottom":294.5,"right":358.5,"top":330.5}},{"unicode":224,"advance":0.5439,"planeBounds":{"left":-0.0620,"bottom":-0.1298,"right":0.6045,"top":0.8701},"atlasBounds":{"left":253.5,"bottom":252.5,"right":277.5,"top":288.5}},{"unicode":225,"advance":0.5439,"planeBounds":{"left":-0.0620,"bottom":-0.1298,"right":0.6045,"top":0.8701},"atlasBounds":{"left":79.5,"bottom":252.5,"right":103.5,"top":288.5}},{"unicode":226,"advance":0.5439,"planeBounds":{"left":-0.0620,"bottom":-0.1298,"right":0.6045,"top":0.8701},"atlasBounds":{"left":376.5,"bottom":252.5,"right":400.5,"top":288.5}},{"unicode":227,"advance":0.5439,"planeBounds":{"left":-0.0620,"bottom":-0.1208,"right":0.6045,"top":0.8513},"atlasBounds":{"left":173.5,"bottom":179.5,"right":197.5,"top":214.5}},{"unicode":228,"advance":0.5439,"planeBounds":{"left":-0.0620,"bottom":-0.1303,"right":0.6045,"top":0.8418},"atlasBounds":{"left":248.5,"bottom":179.5,"right":272.5,"top":214.5}},{"unicode":229,"advance":0.5439,"planeBounds":{"left":-0.0620,"bottom":-0.1215,"right":0.6045,"top":0.9062},"atlasBounds":{"left":307.5,"bottom":293.5,"right":331.5,"top":330.5}},{"unicode":230,"advance":0.8442,"planeBounds":{"left":-0.0756,"bottom":-0.1247,"right":0.9243,"top":0.6530},"atlasBounds":{"left":214.5,"bottom":45.5,"right":250.5,"top":73.5}},{"unicode":231,"advance":0.5234,"planeBounds":{"left":-0.0796,"bottom":-0.3393,"right":0.6148,"top":0.6606},"atlasBounds":{"left":27.5,"bottom":252.5,"right":52.5,"top":288.5}},{"unicode":232,"advance":0.5297,"planeBounds":{"left":-0.0776,"bottom":-0.1298,"right":0.6167,"top":0.8701},"atlasBounds":{"left":53.5,"bottom":252.5,"right":78.5,"top":288.5}},{"unicode":233,"advance":0.5297,"planeBounds":{"left":-0.0776,"bottom":-0.1298,"right":0.6167,"top":0.8701},"atlasBounds":{"left":104.5,"bottom":252.5,"right":129.5,"top":288.5}},{"unicode":234,"advance":0.5297,"planeBounds":{"left":-0.0776,"bottom":-0.1298,"right":0.6167,"top":0.8701},"atlasBounds":{"left":130.5,"bottom":252.5,"right":155.5,"top":288.5}},{"unicode":235,"advance":0.5297,"planeBounds":{"left":-0.0776,"bottom":-0.1303,"right":0.6167,"top":0.8418},"atlasBounds":{"left":265.5,"bottom":216.5,"right":290.5,"top":251.5}},{"unicode":236,"advance":0.2470,"planeBounds":{"left":-0.1487,"bottom":-0.1113,"right":0.2957,"top":0.8608},"atlasBounds":{"left":85.5,"bottom":179.5,"right":101.5,"top":214.5}},{"unicode":237,"advance":0.2470,"planeBounds":{"left":-0.0471,"bottom":-0.1113,"right":0.3972,"top":0.8608},"atlasBounds":{"left":136.5,"bottom":179.5,"right":152.5,"top":214.5}},{"unicode":238,"advance":0.2470,"planeBounds":{"left":-0.1401,"bottom":-0.1113,"right":0.3876,"top":0.8608},"atlasBounds":{"left":153.5,"bottom":179.5,"right":172.5,"top":214.5}},{"unicode":239,"advance":0.2470,"planeBounds":{"left":-0.1530,"bottom":-0.1118,"right":0.4025,"top":0.8325},"atlasBounds":{"left":73.5,"bottom":144.5,"right":93.5,"top":178.5}},{"unicode":240,"advance":0.5859,"planeBounds":{"left":-0.0554,"bottom":-0.1330,"right":0.6389,"top":0.8947},"atlasBounds":{"left":251.5,"bottom":293.5,"right":276.5,"top":330.5}},{"unicode":241,"advance":0.5517,"planeBounds":{"left":-0.0433,"bottom":-0.1159,"right":0.5955,"top":0.8562},"atlasBounds":{"left":34.5,"bottom":179.5,"right":57.5,"top":214.5}},{"unicode":242,"advance":0.5703,"planeBounds":{"left":-0.0761,"bottom":-0.1298,"right":0.6460,"top":0.8701},"atlasBounds":{"left":325.5,"bottom":252.5,"right":351.5,"top":288.5}},{"unicode":243,"advance":0.5703,"planeBounds":{"left":-0.0761,"bottom":-0.1298,"right":0.6460,"top":0.8701},"atlasBounds":{"left":0.5,"bottom":252.5,"right":26.5,"top":288.5}},{"unicode":244,"advance":0.5703,"planeBounds":{"left":-0.0761,"bottom":-0.1298,"right":0.6460,"top":0.8701},"atlasBounds":{"left":359.5,"bottom":294.5,"right":385.5,"top":330.5}},{"unicode":245,"advance":0.5703,"planeBounds":{"left":-0.0761,"bottom":-0.1208,"right":0.6460,"top":0.8513},"atlasBounds":{"left":26.5,"bottom":216.5,"right":52.5,"top":251.5}},{"unicode":246,"advance":0.5703,"planeBounds":{"left":-0.0761,"bottom":-0.1303,"right":0.6460,"top":0.8418},"atlasBounds":{"left":58.5,"bottom":179.5,"right":84.5,"top":214.5}},{"unicode":247,"advance":0.5708,"planeBounds":{"left":-0.0827,"bottom":-0.0375,"right":0.6394,"top":0.7124},"atlasBounds":{"left":14.5,"bottom":14.5,"right":40.5,"top":41.5}},{"unicode":248,"advance":0.5664,"planeBounds":{"left":-0.0761,"bottom":-0.1822,"right":0.6460,"top":0.7066},"atlasBounds":{"left":337.5,"bottom":76.5,"right":363.5,"top":108.5}},{"unicode":249,"advance":0.5512,"planeBounds":{"left":-0.0450,"bottom":-0.1298,"right":0.5938,"top":0.8701},"atlasBounds":{"left":352.5,"bottom":252.5,"right":375.5,"top":288.5}},{"unicode":250,"advance":0.5512,"planeBounds":{"left":-0.0450,"bottom":-0.1298,"right":0.5938,"top":0.8701},"atlasBounds":{"left":203.5,"bottom":252.5,"right":226.5,"top":288.5}},{"unicode":251,"advance":0.5512,"planeBounds":{"left":-0.0450,"bottom":-0.1298,"right":0.5938,"top":0.8701},"atlasBounds":{"left":156.5,"bottom":252.5,"right":179.5,"top":288.5}},{"unicode":252,"advance":0.5512,"planeBounds":{"left":-0.0450,"bottom":-0.1303,"right":0.5938,"top":0.8418},"atlasBounds":{"left":115.5,"bottom":216.5,"right":138.5,"top":251.5}},{"unicode":253,"advance":0.4731,"planeBounds":{"left":-0.1113,"bottom":-0.3289,"right":0.5830,"top":0.8655},"atlasBounds":{"left":64.5,"bottom":376.5,"right":89.5,"top":419.5}},{"unicode":254,"advance":0.5761,"planeBounds":{"left":-0.0513,"bottom":-0.3237,"right":0.6431,"top":0.8706},"atlasBounds":{"left":38.5,"bottom":376.5,"right":63.5,"top":419.5}},{"unicode":255,"advance":0.4731,"planeBounds":{"left":-0.1113,"bottom":-0.3294,"right":0.5830,"top":0.8372},"atlasBounds":{"left":248.5,"bottom":377.5,"right":273.5,"top":419.5}}],"kerning":[]}
        """

        val DEFAULT_MSDF_FONT_INFO: MsdfFontInfo by lazy {
            val meta = Json.Default.decodeFromString<MsdfMeta>(DEFAULT_META_JSON)
            MsdfFontInfo(meta, "fonts/font-roboto-regular.png")
        }
    }

    enum class Backend {
        WEB_GL2,
        WEB_GPU,
        PREFER_WEB_GPU
    }
}