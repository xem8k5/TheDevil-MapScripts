@file:Depends("coreMindustry/utilNext", "调用菜单")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")
@file:Depends("coreMindustry/utilMapRule", "修改单位ai")
@file:Suppress("unused", "unused", "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName",
    "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName",
    "KotlinDeprecation", "KotlinDeprecation", "KotlinDeprecation"
)

package mapScript

import arc.func.Func
import arc.math.Angles
import arc.math.Mathf
import arc.math.geom.Position
import arc.math.geom.Vec2
import arc.struct.ObjectIntMap
import arc.util.Time
import arc.util.Tmp
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.lib.listenPacket2Server
import mindustry.Vars.*
import mindustry.ai.types.MissileAI
import mindustry.content.*
import mindustry.core.World
import mindustry.entities.Units
import mindustry.entities.units.UnitController
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.game.Teams.TeamData
import mindustry.gen.*
import mindustry.type.StatusEffect
import mindustry.type.UnitType
import mindustry.world.blocks.campaign.LaunchPad
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import java.lang.Float.min
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**@author xkldklp
 * https://mdt.wayzer.top/v2/map/14668/latest
 */
name = "Lord of War"
//low...?
//屎山战争 开堆！

val menu = contextScript<coreMindustry.UtilNext>()

val T1UnitCost by lazy { state.rules.tags.getInt("@T1UC", 8) }
val T1Units = arrayOf(
    UnitTypes.dagger,
    UnitTypes.nova,
    UnitTypes.merui,
    UnitTypes.elude,
    UnitTypes.stell
)
val T2UnitCost by lazy { state.rules.tags.getInt("@T2UC", 32) }
val T2Units = arrayOf(
    UnitTypes.pulsar,
    UnitTypes.poly,
    UnitTypes.atrax,
    UnitTypes.avert,
    UnitTypes.locus
)
val T3UnitCost by lazy { state.rules.tags.getInt("@T3UC", 128) }
val T3Units = arrayOf(
    UnitTypes.mace,
    UnitTypes.mega,
    UnitTypes.cleroi,
    UnitTypes.zenith,
    UnitTypes.precept
)
val T4UnitCost by lazy { state.rules.tags.getInt("@T4UC", 512) }
val T4Units = arrayOf(
    UnitTypes.spiroct,
    UnitTypes.cyerce,
    UnitTypes.anthicus,
    UnitTypes.antumbra,
    UnitTypes.vanquish
)
val T5UnitCost by lazy { state.rules.tags.getInt("@T5UC", 2048) }
val T5Units = arrayOf(
    UnitTypes.arkyid,
    UnitTypes.vela,
    UnitTypes.tecta,
    UnitTypes.sei,
    UnitTypes.scepter
)
val LordUnitCost by lazy { state.rules.tags.getInt("@LUC", 65536) }
val LordUnits = arrayOf(
    UnitTypes.toxopid,
    UnitTypes.aegires,
    UnitTypes.collaris,
    UnitTypes.eclipse,
    UnitTypes.conquer,
    UnitTypes.disrupt
)

fun UnitType?.cost(): Int{
    return when(this){
        in T1Units -> T1UnitCost
        in T2Units -> T2UnitCost
        in T3Units -> T3UnitCost
        in T4Units -> T4UnitCost
        in T5Units -> T5UnitCost
        in LordUnits -> LordUnitCost
        else -> 0
    }
}
fun UnitType?.levelUnits(): Array<UnitType>?{
    return when(this){
        in T1Units -> T1Units
        in T2Units -> T2Units
        in T3Units -> T3Units
        in T4Units -> T4Units
        in T5Units -> T5Units
        in LordUnits -> LordUnits
        else -> null
    }
}
fun Int.levelUnits(): Array<UnitType>?{
    return when(this){
        1 -> T1Units
        2 -> T2Units
        3 -> T3Units
        4 -> T4Units
        5 -> T5Units
        6 -> LordUnits
        else -> null
    }
}
fun UnitType?.level(): Int{
    return when(this){
        in T1Units -> 1
        in T2Units -> 2
        in T3Units -> 3
        in T4Units -> 4
        in T5Units -> 5
        in LordUnits -> 6
        else -> 0
    }
}

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}

val teamCoins: ObjectIntMap<Team> = ObjectIntMap()//队伍银行的金钱
fun Team.coins(): Int { return teamCoins[this] }
fun Team.removeCoin(amount: Int) { teamCoins.put(this, coins() - amount) }
fun Team.addCoin(amount: Int) { teamCoins.put(this, coins() + amount)}
fun Team.setCoin(amount: Int) { teamCoins.put(this, amount) }

val playerInputing:MutableMap<String, Boolean> = mutableMapOf()
val playerLastSendText:MutableMap<String, String?> = mutableMapOf()

val playerCoins: ObjectIntMap<String> = ObjectIntMap()//玩家的金钱
fun Player.coins(): Int { return playerCoins[uuid()]}
fun Player.removeCoin(amount: Int) {
    playerCoins.put(uuid(), coins() - amount)
    sendMessage("[red]失去 $amount 金币")
}
fun Player.addCoin(amount: Int, quiet: Boolean = false) {
    playerCoins.put(uuid(), coins() + amount)
    if(!quiet) sendMessage("[green]得到 $amount 金币")
}
fun Player.setCoin(amount: Int) {
    playerCoins.put(uuid(), amount)
    sendMessage("[green]金币被设置为 $amount")
}

val playerUnitCap: ObjectIntMap<String> = ObjectIntMap()//玩家的军团单位上限
fun Player.unitCap(): Int{ return playerUnitCap[uuid()] }
fun Player.unitCap(unitCap: Int){ playerUnitCap.put(uuid(), unitCap)}

val unitOwner: MutableMap<mindustry.gen.Unit, String> = mutableMapOf()//单位的领主
fun mindustry.gen.Unit.owner(): String? { return unitOwner.getOrDefault(this, null) }

val playerUnit: MutableMap<String, UnitType?> = mutableMapOf()//玩家统领单位类型
fun Player.unitType(): UnitType?{ return playerUnit[uuid()] }
fun Player.unitType(unitType: UnitType){  playerUnit[uuid()] = unitType}

val playerLordUnitType: MutableMap<String, UnitType?> = mutableMapOf()//玩家领主级单位类型
fun Player.lordUnitType(): UnitType?{ return playerLordUnitType[uuid()] }
fun Player.lordUnitType(unitType: UnitType){  playerLordUnitType[uuid()] = unitType}

val playerLordUnit: MutableMap<String, mindustry.gen.Unit> = mutableMapOf()//玩家领主级单位
fun Player.lordUnit(): mindustry.gen.Unit?{ return playerLordUnit[uuid()] }
fun Player.lordUnit(unit: mindustry.gen.Unit){ playerLordUnit[uuid()] = unit}

val playerLordCooldown: ObjectIntMap<String> = ObjectIntMap()//玩家领主级单位召唤冷却
fun Player.checkLordCooldown(): Boolean{ return playerLordCooldown.get(uuid()) <= Time.timeSinceMillis(startTime) }
fun Player.setLordCooldown(time: Float){ playerLordCooldown.put(uuid(), (Time.timeSinceMillis(startTime) + time * 1000).toInt()) }

val cooldown: ObjectIntMap<String> = ObjectIntMap()//玩家收获城市资源冷却
val startTime by lazy { Time.millis() }
fun Player.checkCooldown(): Boolean{ return cooldown.get(uuid()) <= Time.timeSinceMillis(startTime) }
fun Player.setCooldown(time: Float){ cooldown.put(uuid(), (Time.timeSinceMillis(startTime) + time * 1000).toInt()) }

data class PadData(
    var targetPad: LaunchPad.LaunchPadBuild? = null,
    var name: String = ""
)

val padData by lazy { mutableMapOf<LaunchPad.LaunchPadBuild, PadData?>() }
fun LaunchPad.LaunchPadBuild.padData(): PadData {
    if(padData[this] == null) {
        val data = PadData()
        padData[this] = data
    }
    return padData[this]!!
}

@Suppress("unused", "unused", "unused", "unused", "unused")
data class TeamTech(
    val team: Team,
    var exp: Float = 0f,
    var level: Int = 1,
    var techPoint: Int = 0,
    var unitCostMultiplier: Float = 1f,
    val bulletStatusEffect: MutableMap<StatusEffect, Float> = mutableMapOf(),
    var bulletStatusEffectProbability: Float = 0.05f,
    val unitStatusEffect: MutableMap<StatusEffect, Float> = mutableMapOf(),
    var unitStatusEffectProbability: Float = 0.25f,
    var lordUnitHealthMultiplier: Float = 1f,
    var lordUnitSpawnInvincibleTime: Float = 0f,
    var lordUnitExplosionMultiplier: Float = 1f,
    var lordUnitSpawnTimeMultiplier: Float = 1f,
    var lordUnitCostMultiplier: Float = 1f,
    var upgradeAndRollMultiplier: Float = 1f,
    var randomSeed: Int = Random.nextInt(0, 999999)
){
    fun checkEffectBulletHit(): Boolean{
        return Random.nextFloat() <= bulletStatusEffectProbability
    }

    fun checkUnitEffectApply(): Boolean{
        return Random.nextFloat() <= unitStatusEffectProbability
    }

    fun techCost(): Int{
        var cores = 0f
        state.teams.getActive().forEach {
            cores += it.cores.size
        }
        return (((level + 1) * 3.5f + 5f).pow(3) * (team.cores().size / cores) * 9).toInt()
    }
}

val teamTech by lazy { mutableMapOf<Team, TeamTech?>() }
fun Team.techData(): TeamTech {
    if(teamTech[this] == null) {
        val data = TeamTech(this)
        teamTech[this] = data
    }
    return teamTech[this]!!
}

class CityType(
    val name: String,
    val coinsMultiplier: Float = 1f,
    val healMultiplier: Float = 1f,
    val unitCostMultiplier: Float = 1f,
    val researchMultiplier: Float = 1f,
    val cost: Float = 1.2f
)

class CityTypes(
    val default: CityType = CityType("[white]均衡城市", cost = 0.1f),
    val Economic: CityType = CityType("[yellow]经济城市", 1.3f, 0.5f, 1.15f, 0.9f, 1.2f),
    val Logistics: CityType = CityType("[acid]补给城市", 0.75f, 1.5f, 1.15f, 0.9f, 0.8f),
    val War: CityType = CityType("[crimson]军工城市", 0.5f, 1f, 0.95f, 0.8f, 1.8f),
    val WarEconomic: CityType = CityType("[goldenrod]军工经济城市", 1.3f, 0.25f, 0.95f, 0.6f, 2.9f),
    val LogisticsEconomic: CityType = CityType("[olive]补给经济城市", 1.3f, 1.5f, 1.25f, 0.8f, 2.1f),
    val WarLogistics: CityType = CityType("[tan]军工补给城市", 0.45f, 1.5f, 0.95f, 0.6f, 2.4f),
    val WarLogisticsEconomic: CityType = CityType("[cyan]军工补给经济城市", 1.5f, 2f, 0.9f, 0.3f, 5.2f),
    val ResearchI: CityType = CityType("[sky]科研城市", 0.75f, 0.75f, 1.15f, 2f, 0.8f),
    val ResearchII: CityType = CityType("[sky]科研中心", 0.5f, 0.5f, 1.25f, 4f, 2.4f),
    val ResearchIII: CityType = CityType("[sky]科研基地", 0.25f, 0.25f, 1.5f, 8f, 5.2f),
    ){
    fun toArray(): Array<Array<CityType>>{
        return arrayOf(
            arrayOf(default),
            arrayOf(Economic,Logistics,War),
            arrayOf(WarEconomic,LogisticsEconomic,WarLogistics),
            arrayOf(WarLogisticsEconomic),
            arrayOf(ResearchI,ResearchII,ResearchIII)
        )
    }
}

val CityTypes by lazy { CityTypes() }

data class CityData(
    var coins: Int = 0,
    var lord: String? = null,
    var cityType: CityType = CityTypes().default
)

val cityData by lazy { mutableMapOf<CoreBuild, CityData?>() }
fun CoreBuild.cityData(): CityData {
    if(cityData[this] == null) {
        val data = CityData()
        cityData[this] = data
    }
    return cityData[this]!!
}
fun CoreBuild.coins(): Int { return cityData().coins }
fun CoreBuild.removeCoin(amount: Int) { cityData().coins -= amount }
fun CoreBuild.addCoin(amount: Int) { cityData().coins += amount}
fun CoreBuild.setCoin(amount: Int) { cityData().coins = amount }
fun CoreBuild.lord(): String? { return cityData().lord }
fun CoreBuild.lord(uuid: String) { cityData().lord = uuid }

fun CoreBuild.level():Int {
    return when(block){
        Blocks.coreShard -> 1
        Blocks.coreFoundation -> 2
        Blocks.coreBastion -> 3
        Blocks.coreNucleus -> 4
        Blocks.coreCitadel -> 5
        Blocks.coreAcropolis -> 6
        else -> 0
    }
}
fun CoreBuild.levelText(noColor: Boolean = false): String {
    return "[white]${if (!noColor && coins() >= maxHealth && block != Blocks.coreAcropolis) "${Iconc.up}[cyan]" else "${if (!noColor) Iconc.upload else ""}"}${when (level()) {
        1 -> "村庄"
        2 -> "乡镇"
        3 -> "庄园"
        4 -> "城市"
        5 -> "大城市"
        6 -> "首都"
        else -> ""
    }}[white]"
}

fun Int.levelText(): Char{
    return when {
        this >= 4096 -> Iconc.blockItemSource
        this >= 2048 -> Iconc.blockFluxReactor
        this >= 1024 -> Iconc.blockNeoplasiaReactor
        this >= 512 -> Iconc.blockImpactReactor
        this >= 256 -> Iconc.blockThoriumReactor
        this >= 128 -> Iconc.blockEruptionDrill
        this >= 64 -> Iconc.blockImpactDrill
        this >= 48 -> Iconc.blockBlastDrill
        this >= 32 -> Iconc.blockLaserDrill
        this >= 12 -> Iconc.blockPneumaticDrill
        else -> Iconc.blockMechanicalDrill
    }
}

fun Player.createUnit(core: CoreBuild,unitType: UnitType? = unitType(), team: Team = team()): Boolean{
    if (unitType == null || Groups.unit.filter { u -> u.owner() == uuid() }.size >= unitCap()) return false
    var times = 0
    val spawnRadius = 5
    val unit = unitType.create(team)
    while (true) {
        Tmp.v1.rnd(spawnRadius.toFloat() * tilesize)

        val sx = core.x + Tmp.v1.x
        val sy = core.y + Tmp.v1.y

        if (unit.canPass(World.toTile(sx), World.toTile(sy))) {
            unit.set(sx, sy)
            unitOwner[unit] = uuid()
            break
        }

        if (++times > 20) {
            return false
        }
    }
    unit.apply {
        unitOwner[this] = uuid()
        team.techData().unitStatusEffect.forEach { (t, u) ->
            if (team.techData().checkUnitEffectApply()) {
                unit.apply(t, u)
            }
        }
        add()
    }
    return true
}
fun Player.createLordUnit(core: CoreBuild,unitType: UnitType? = lordUnitType()): Boolean{
    if (unitType == null) return false
    var times = 0
    val spawnRadius = 5
    val unit = unitType.create(team())
    unit.apply {
        while (true){
            Tmp.v1.rnd(spawnRadius.toFloat() * tilesize)

            val sx = core.x + Tmp.v1.x
            val sy = core.y + Tmp.v1.y

            if (canPass(World.toTile(sx), World.toTile(sy))) {
                set(sx, sy)
                break
            }

            if (++times > 20) {
                return false
            }
        }
        launch(Dispatchers.game){
            unitOwner[unit] = uuid()
            lordUnit(unit)
            val spawnTime = Time.millis()
            while(Time.timeSinceMillis(spawnTime) / 1000f <= 120f * team.techData().lordUnitSpawnTimeMultiplier){
                Call.label("${unit.type.emoji()}[#${team().color}]领主级单位降临[white]${unit.type.emoji()}\n$name [white]${(120f * team.techData().lordUnitSpawnTimeMultiplier - Time.timeSinceMillis(spawnTime) / 1000f).format()}", 0.2026f, x, y)
                Call.effect(Fx.spawnShockwave, x, y, 0f, team().color)
                delay(200)
            }
            apply(StatusEffects.boss, Float.MAX_VALUE)
            apply(StatusEffects.overdrive, Float.MAX_VALUE)
            apply(StatusEffects.overclock, Float.MAX_VALUE)
            apply(StatusEffects.shielded, Float.MAX_VALUE)
            apply(StatusEffects.invincible, team.techData().lordUnitSpawnInvincibleTime * 60f)
            add()
            maxHealth *= team.techData().lordUnitHealthMultiplier
            health = maxHealth
            unit(unit)
            Call.announce("$name [#${team().color}]领主级单位\n!${unit.type.emoji()}出征${unit.type.emoji()}!")
            Call.sendMessage("$name [#${team().color}]领主级单位${unit.type.emoji()}出征${unit.type.emoji()}!")
            Call.logicExplosion(team, x, y, 32f * 8f * team.techData().lordUnitExplosionMultiplier, 7500f * team.techData().lordUnitExplosionMultiplier, true, true, true)
            Call.effect(Fx.impactReactorExplosion, x, y, 0f, team().color)
            var damageStartTime = Time.millis()
            val maxMaxHealth = maxHealth
            while (!dead) {
                if (!isPlayer){
                    apply(StatusEffects.unmoving, 4f * 60f)
                    apply(StatusEffects.disarmed, 4f * 60f)
                    unapply(StatusEffects.shielded)
                    maxHealth = maxMaxHealth * (1f - (Time.timeSinceMillis(damageStartTime) / 1000f / 300f))
                    Call.label("[#${team().color}]未被操控的领主级单位\n${health.format(1)}/${maxHealth.format(1)}\n[red]距离直接死亡还剩${(300f - (Time.timeSinceMillis(damageStartTime) / 1000f)).format()}s", 0.2026f, x, y)
                    clampHealth()
                    if (maxHealth <= 0) kill()
                }else{
                    apply(StatusEffects.shielded, Float.MAX_VALUE)
                    maxHealth = maxMaxHealth
                    damageStartTime = Time.millis()
                }
                delay(200)
            }
        }
    }
    return true
}

suspend fun Player.cityMenu(core: CoreBuild) {
    @Suppress("KotlinDeprecation")
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]城市页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]当前城市拥有金币[#${team().color}]${Iconc.blockCliff}${core.coins()}
            [cyan]你当前拥有金币[#${team().color}]${Iconc.blockCliff}${coins()}
            [cyan]升级城市将会变为此城市领主！会自动收集一半的金币
            [red]核心机收取资源将减少！
        """.trimIndent()
    ) {
        if(checkCooldown()){
            this += listOf("[green]收取资源" to {
                if (core.dead || !core.isValid || core.team() != team()){
                    sendMessage("[red]查无此城")
                } else {
                    if (checkCooldown()) {
                        val amount =
                            (core.coins() * if (unit().type != UnitTypes.alpha) Random.nextFloat() else Random.nextFloat() / 4).toInt()
                        addCoin(amount)
                        core.removeCoin(amount)
                        setCooldown(
                            (amount / max(
                                Time.timeSinceMillis(startTime) / 1000 / 30,
                                1L
                            ) / core.level()).toFloat()
                        )
                        Call.transferItemEffect(Items.copper, core.x, core.y, unit())
                    }
                }
            })
        }
        else{
            this += listOf("[red]收取冷却！${cooldown[uuid()] / 1000 - Time.timeSinceMillis(startTime) / 1000}s Left!" to {
                cityMenu(core)
            })
        }
        if(core.level() < 6) {
            if (core.coins() >= core.maxHealth && core.block != Blocks.coreAcropolis) {
                this += listOf("[cyan]可升级城市！\n[#${team().color}]${Iconc.blockCliff}${core.maxHealth}" to {
                    if (core.dead || !core.isValid || core.team() != team()){
                        sendMessage("[red]查无此城")
                    } else {
                        if (core.coins() >= core.maxHealth && core == (core.tile.build as CoreBuild)) {
                            val cost = core.maxHealth.toInt()
                            val coins = core.coins()
                            val tile = core.tile
                            val target = when (core.block) {
                                Blocks.coreShard -> Blocks.coreFoundation
                                Blocks.coreFoundation -> Blocks.coreBastion
                                Blocks.coreBastion -> Blocks.coreNucleus
                                Blocks.coreNucleus -> Blocks.coreCitadel
                                Blocks.coreCitadel -> Blocks.coreAcropolis
                                else -> Blocks.coreShard
                            }
                            tile.setNet(target, core.team, 0)
                            (tile.build as CoreBuild).setCoin(coins - cost)
                            (tile.build as CoreBuild).lord(uuid())
                            Groups.player.forEach {
                                if (it.team() == team() || fogControl.isVisible(
                                        it.team(),
                                        tile.worldx(),
                                        tile.worldy()
                                    )
                                )
                                    it.sendMessage(
                                        "[#${core.team.color}]位于[${World.toTile(core.x)},${World.toTile(core.y)}]的 [white]${core.block.emoji()} [#${core.team.color}]已经被[white] $name [#${core.team.color}]升级为 [white]${tile.build.block.emoji()}[white]${
                                            (tile.build as CoreBuild).levelText(
                                                true
                                            )
                                        }"
                                    )
                                else
                                    it.sendMessage(
                                        "[#${core.team.color}]位于[${
                                            World.toTile(core.x) + Random.nextInt(
                                                -60,
                                                60
                                            )
                                        },${
                                            World.toTile(core.y) + Random.nextInt(
                                                -60,
                                                60
                                            )
                                        }]附近的 ??? 已经被[white] $name [#${core.team.color}]升级为 ???"
                                    )
                            }
                        }
                    }
                })
            } else {
                this += listOf("[lightgray]城市金币不足以升级城市！\n${Iconc.blockCliff}${core.maxHealth}" to {
                    cityMenu(core)
                })
            }
        }
        this += listOf(
            "[green]城市" to {lordMenu(core)},
            "转型" to {cityTypeMenu(core)}
        )
        this += listOf(
            "军团" to {warMenu(core)},
            "[green]城市" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
        if (admin && Groups.player.size() <= 5){
            this += listOf(
                "[red]<ADMIN>自己加1000000金币" to { addCoin(1000000) },
                "[red]<ADMIN>城市加1000000金币" to { core.addCoin(1000000) },
                "[red]<ADMIN>随机领主" to { if (lordUnitType() != null) lordUnitType(lordUnitType().levelUnits()!!.random()) }
            )
        }
    }
}
suspend fun Player.cityTypeMenu(core: CoreBuild) {
    @Suppress("KotlinDeprecation")
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]城市页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]你当前拥有金币[#${team().color}]${Iconc.blockCliff}${coins()}
            [cyan]转型城市将改变此城市类型!
            [cyan]不同的城市类型将会提供不同的强力增强!
            [lightgray]当前城市类型加成：
            [lightgray]金钱增长倍率：${core.cityData().cityType.coinsMultiplier}
            [lightgray]单位治疗倍率：${core.cityData().cityType.healMultiplier}
            [lightgray]单位花费倍率：${(1 - (1 - core.cityData().cityType.unitCostMultiplier) * core.level() / 3).format()}
            [lightgray]科研倍率：${core.cityData().cityType.researchMultiplier}
        """.trimIndent()
    ) {
        if (core.cityData().cityType.name == "[white]均衡城市" && core.cityData().cityType != CityTypes.default) core.cityData().cityType = CityTypes.default
        fun cityType(type: CityType, costMultiplier: Float = 1f): Pair<String, suspend () -> Unit> {
            return if (core.cityData().cityType == type) "[lightgray]目前城市类型\n${type.name}" to suspend {
                cityTypeMenu(core)
            } else if (type.name == "[white]均衡城市" && core.cityData().cityType.name != "[white]均衡城市")
                "${type.name}\n" +
                        "[red]不允许转型至此类型！" to suspend {
                    cityTypeMenu(core)
                }
                else if (coins() >= type.cost * core.maxHealth * costMultiplier) "${type.name}\n" +
                    "[#${team().color}]${Iconc.blockCliff}${(type.cost * core.maxHealth * costMultiplier).toInt()}" to suspend {
                if (coins() >= type.cost * core.maxHealth * costMultiplier && core.isValid){
                    removeCoin((type.cost * core.maxHealth * costMultiplier).toInt())
                    val lastCoreType = core.cityData().cityType
                    core.cityData().cityType = type
                    Groups.player.forEach {
                        if (it.team() == team() || fogControl.isVisible(
                                it.team(),
                                core.tile.worldx(),
                                core.tile.worldy()
                            )
                        )
                            it.sendMessage(
                                "[#${core.team.color}]位于[${World.toTile(core.x)},${World.toTile(core.y)}]的 [white]${core.block.emoji()}${lastCoreType.name}[white] 已经被[white] $name [#${core.team.color}]转型为 [white]${core.block.emoji()}[white]${core.cityData().cityType.name}"
                            )
                        else
                            it.sendMessage(
                                "[#${core.team.color}]位于[${
                                    World.toTile(core.x) + Random.nextInt(
                                        -60,
                                        60
                                    )
                                },${
                                    World.toTile(core.y) + Random.nextInt(
                                        -60,
                                        60
                                    )
                                }]附近的 ??? 已经被[white] $name [#${core.team.color}]转型为 ???"
                            )
                    }
                } else {
                    sendMessage("[red]转型失败！")
                }
            } else "[lightgray]${type.name}\n[lightgray]${Iconc.blockCliff}${(type.cost * core.maxHealth * costMultiplier).toInt()}" to suspend {
                cityTypeMenu(core)
            }
        }
        CityTypes.toArray().forEach { typesArray ->
            val list = mutableListOf<Pair<String, suspend () -> Unit>>()
            typesArray.forEach { if (core.cityData().cityType.name == "[white]均衡城市" && it.name != "[cyan]军工补给经济城市") list.add(cityType(it, 0.45f)) else list.add(cityType(it))}
            add(list)
        }

        this += listOf(
            "城市" to {lordMenu(core)},
            "[green]转型" to {cityTypeMenu(core)}
        )
        this += listOf(
            "军团" to {warMenu(core)},
            "[green]城市" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
    }
}
suspend fun Player.warMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]战争页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]你当前拥有金币[#${team().color}]${Iconc.blockCliff}${coins()}
            [lightgray]当前军团等级:${unitType().level()}
        """.trimIndent()
    ) {
        if (unitType() == null){
            this += listOf(
                "[red]你还没有军团！\n点击抽取你的军团单位！" to {
                    val randomUnit = T1Units.random()
                    unitType(randomUnit)
                    Call.announce(con, "[cyan]抽取新军团单位:[white]${randomUnit.emoji()}")
                    unitCap(16)
                    warMenu(core)
                }
            )
        }else{
                this += listOf(
                    (if (coins() >= (unitType().cost() * 5 * team().techData().upgradeAndRollMultiplier).toInt()) "[red]军团不满意？\n" + "重新抽取军团单位！\n" + "[#${team().color}]${Iconc.blockCliff}${(unitType().cost() * 5 * team().techData().upgradeAndRollMultiplier).toInt()}"
                    else "[lightgray]你需要${Iconc.blockCliff}${(unitType().cost() * 5 * team().techData().upgradeAndRollMultiplier).toInt()}来重新抽取军团单位") to {
                        if (coins() >= (unitType().cost() * 5 * team().techData().upgradeAndRollMultiplier).toInt()) {
                            val lastUnit = unitType()
                            var randomUnit = unitType().levelUnits()!!.random()
                            var times = 0
                            while (times <= 10){
                                if (lastUnit != randomUnit) break
                                randomUnit = unitType().levelUnits()!!.random()
                                times++
                            }
                            unitType(randomUnit)
                            Call.announce(con, "[cyan]抽取新军团单位:[white]${randomUnit.emoji()}")
                            removeCoin((unitType().cost() * 5 * team().techData().upgradeAndRollMultiplier).toInt())
                            warMenu(core)
                        } else {
                            sendMessage("[red]金钱不足！")
                        }
                    },
                    if (coins() >= (unitType().cost() * 25 * team().techData().upgradeAndRollMultiplier).toInt() && unitType().level() < 5)
                        "[cyan]军团可升级!\n[#${team().color}]${Iconc.blockCliff}${(unitType().cost() * 25 * team().techData().upgradeAndRollMultiplier).toInt()}" to {
                            if (coins() >= (unitType().cost() * 25 * team().techData().upgradeAndRollMultiplier).toInt() && unitType().level() < 5) {
                                removeCoin((unitType().cost() * 25 * team().techData().upgradeAndRollMultiplier).toInt())
                                val randomUnit = (unitType().level() + 1).levelUnits()!!.random()
                                unitType(randomUnit)
                                Call.announce(con, "[cyan]抽取新军团单位:[white]${randomUnit.emoji()}")
                                warMenu(core)
                            } else {
                                sendMessage("[red]金钱不足！")
                            }
                        } else if (unitType().level() < 5)
                        "[cyan]你需要金币[#${team().color}]${Iconc.blockCliff}${unitType().cost() * 25 * team().techData().upgradeAndRollMultiplier}[cyan]来升级军团等级" to {
                            warMenu(core)
                        } else
                        "[cyan]军团等级已满！" to {
                            warMenu(core)
                        }
                )
            this += listOf(
                "${unitType()!!.emoji()}[#${team().color}]${Iconc.blockCliff}${(unitType().cost() * (team().techData().unitCostMultiplier - (1 - core.cityData().cityType.unitCostMultiplier) * core.level() / 3)).toInt()}[white]${unitType()!!.emoji()}" to {
                    if (core.dead || !core.isValid || core.team() != team()){
                        sendMessage("[red]查无此城")
                    } else {
                        if (coins() >= (unitType().cost() * (team().techData().unitCostMultiplier - (1 - core.cityData().cityType.unitCostMultiplier) * core.level() / 3)).toInt()) {
                            if (createUnit(core)) {
                                removeCoin((unitType().cost() * (team().techData().unitCostMultiplier - (1 - core.cityData().cityType.unitCostMultiplier) * core.level() / 3)).toInt())
                                warMenu(core)
                            } else {
                                sendMessage("[red]生成失败！")
                            }
                        } else {
                            sendMessage("[red]金钱不足！")
                        }
                    }
                }
            )
            if (unitType().level() == 5){
                if (lordUnit()?.dead == false){
                    if (lordUnit() != unit())
                        this += listOf(
                            "[cyan]领主级单位未死亡\n附身你的领主级单位！" to {
                                unit(lordUnit())
                            }
                        )
                } else {
                    if (lordUnitType() == null) {
                        this += listOf(
                            "[cyan]领主级单位解锁！\n点击抽取你的领主级单位！" to {
                                val randomUnit = LordUnits.random()
                                lordUnitType(randomUnit)
                                Call.announce(con, "[cyan]抽取领主级单位:[white]${randomUnit.emoji()}")
                                Call.sendMessage("[white]$name [#${team().color}]抽取了领主级单位${randomUnit.emoji()}!")
                                warMenu(core)
                            }
                        )
                    }
                    if (checkLordCooldown() && lordUnitType() != null) {
                        this += listOf(
                            "[cyan]领主级单位冷却完毕！\n点击出征！\n${(lordUnitType().cost() * team().techData().lordUnitCostMultiplier).toInt()}${lordUnitType()!!.emoji()}" to {
                                if (core.dead || !core.isValid || core.team() != team()) {
                                    sendMessage("[red]查无此城")
                                } else {
                                    if (coins() >= (lordUnitType().cost() * team().techData().lordUnitCostMultiplier).toInt()) {
                                        if (createLordUnit(core)) {
                                            removeCoin((lordUnitType().cost() * team().techData().lordUnitCostMultiplier).toInt())
                                            Call.sendMessage("$name [#${team().color}]领主级单位${lordUnitType()!!.emoji()}准备出征!")
                                            val a1 = 0.05f+ Random.nextFloat() * 0.15f
                                            val a2 = team().techData().lordUnitSpawnTimeMultiplier * (1f - (0.9f + Random.nextFloat() * 0.1f))
                                            val a3 = 0.1f + Random.nextFloat() * 0.2f
                                            val a4 = 5f +Random.nextFloat() * 5f
                                            val a5 = 0.1f +Random.nextFloat() * 0.1f
                                            team().techData().lordUnitHealthMultiplier += a1
                                            team().techData().lordUnitSpawnTimeMultiplier -= a2
                                            team().techData().lordUnitExplosionMultiplier += a3
                                            team().techData().lordUnitSpawnInvincibleTime += a4
                                            team().techData().lordUnitCostMultiplier += a5
                                            Call.sendMessage(buildString {
                                                appendLine("[#${team().color}]现在的领主增幅：")
                                                appendLine("[#${team().color}]领主生命倍率：[white]${(team().techData().lordUnitHealthMultiplier * 100f).format()}% [green](+${(a1 * 100f).format()}%)")
                                                appendLine("[lightgray]会导致不同步，但是血量确实加了")
                                                appendLine("[#${team().color}]领主降临时间倍率：[white]${(team().techData().lordUnitSpawnTimeMultiplier * 100f).format()}% [green](-${(a2 * 100f).format()}%)")
                                                appendLine("[#${team().color}]领主降临后爆炸倍率：[white]${(team().techData().lordUnitExplosionMultiplier * 100f).format()}% [green](+${(a3 * 100f).format()}%)")
                                                appendLine(
                                                    "[#${team().color}]领主降临后临时无敌时间：[white]${
                                                        (team().techData().lordUnitSpawnInvincibleTime).format(
                                                            1
                                                        )
                                                    }s [green](+${a4.format(1)}s)"
                                                )
                                                appendLine("[#${team().color}]领主降临花费倍率：[white]${(team().techData().lordUnitCostMultiplier * 100f).format()}% [red](+${(a5 * 100f).format()}%)")
                                            })
                                            setLordCooldown(900f * team().techData().lordUnitSpawnTimeMultiplier)
                                            lordUnitType(lordUnitType().levelUnits()!!.random())
                                            warMenu(core)
                                        } else {
                                            sendMessage("[red]生成失败！")
                                        }
                                    } else {
                                        sendMessage("[red]金钱不足！")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        this += listOf(
            "[green]军团" to {warMenu(core)},
            "城市" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
    }
}
suspend fun Player.bankMenu(core: CoreBuild) {
    var cores = 0f
    state.teams.getActive().forEach {
        cores += it.cores.size
    }
    val rate = 0.001f * (0.5f - team().cores().size / cores) * 2f
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]银行页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]你当前拥有金币[#${team().color}]${Iconc.blockCliff}${coins()}
            [cyan]银行当前拥有金币[#${team().color}]${Iconc.blockCliff}${team().coins()}
            [cyan]银行金币可供所有队友存取！
            [yellow]银行现在每秒利息：${(team().coins() * rate).toInt()}
            [lightgray]利率：{队伍金钱(${team().coins()}) * 基础利率(0.1%) * 变动利率[0.5 - 队伍核心数(${team().cores().size}) / 全场核心数(${cores.toInt()})] * 2(${((0.5f - team().cores().size / cores) * 2f * 100f).format()}%)}(${(rate * 100f).format()}%)
        """.trimIndent()
    ) {
    playerInputing[uuid()] = false
    this += listOf(
            "存金币[#${team().color}]${Iconc.blockCliff}" to {
                    val playerLastText = playerLastSendText[uuid()]
                    sendMessage("------------\n[yellow]请输入所存数量\n[white]------------")
                    val startInputTime = Time.millis()
                    var fail = true
                    var coin = 0
                    playerInputing[uuid()] = true
                    while (Time.timeSinceMillis(startInputTime) / 1000 <= 15) {
                        if (playerInputing[uuid()] == false) break
                        if (playerLastText != playerLastSendText[uuid()]){
                            val amount = playerLastSendText[uuid()]?.toIntOrNull()
                            if (amount == null || amount <= 0 || amount > coins()) break
                            team().addCoin(amount)
                            removeCoin(amount)
                            coin = amount
                            fail = false
                            break
                        }
                        yield()
                    }
                    playerLastSendText[uuid()] = ""
                    if (fail) {
                        sendMessage("存钱失败！")
                    } else {
                        Call.sendMessage("$name [#${team().color}]往队伍银行存储${Iconc.blockCliff}$coin")
                    }

            },
        "取金币[#${team().color}]${Iconc.blockCliff}" to {
                if (team().coins() > 0) {
                    val playerLastText = playerLastSendText[uuid()]
                    sendMessage("------------\n[yellow]请输入所取数量\n[white]------------")
                    val startInputTime = Time.millis()
                    var fail = true
                    var coin = 0
                    playerInputing[uuid()] = true
                    while (Time.timeSinceMillis(startInputTime) / 1000 <= 15) {
                        if (playerInputing[uuid()] == false) break
                        if (playerLastText != playerLastSendText[uuid()]){
                            val amount = playerLastSendText[uuid()]?.toIntOrNull()
                            if (amount == null || amount > team().coins() || amount <= 0) break
                            team().removeCoin(amount)
                            addCoin(amount)
                            coin = amount
                            fail = false
                            break
                        }
                        yield()
                    }
                    playerLastSendText[uuid()] = ""
                    if (fail) {
                        sendMessage("取钱失败！")
                    } else {
                        Call.sendMessage("$name [#${team().color}]往队伍银行取出${Iconc.blockCliff}$coin")
                    }
                } else {
                    sendMessage("[red]队伍银行没钱给你取！")
                }
            }
    )
    this += listOf(
         "[yellow]科伦坡富豪榜" to {
             sendMessage(buildString {
                 appendLine("[cyan]谁是最有钱的资本家?")
                 var target = Groups.player.maxByOrNull { it.coins() }!!
                 appendLine("[yellow]是坐拥[#${target.team().color}]${Iconc.blockCliff}${target.coins()}[yellow]的[white]${target.name}[yellow]哒!")
                 appendLine("[cyan]谁是你家里最有钱的资本家?")
                 target = Groups.player.filter { it.team() == team() }.maxByOrNull { it.coins() }!!
                 appendLine("[yellow]是坐拥[#${target.team().color}]${Iconc.blockCliff}${target.coins()}[yellow]的[white]${target.name}[yellow]哒!")
                 append("[cyan]看看你队伍里资本家们的财富和军团！")
                 Groups.player.filter { it.team() == team() }.sortedBy { it.coins() }.reversed().forEach {
                     append("\n[white]${it.name} ")
                     append("[#${it.team().color}]${Iconc.blockCliff}${it.coins()}")
                     if(it.unitType() != null)
                        append("[white]  ${it.unitType()!!.emoji()}")
                     if(it.lordUnitType() != null)
                         append("|${it.lordUnitType()!!.emoji()} ${ if(it.checkLordCooldown()) "[green]准备就绪" else "[red]${playerLordCooldown[it.uuid()] / 1000 - Time.timeSinceMillis(startTime) / 1000}s" }")
                 }
             })
         }
    )
    this += listOf(
        "军团" to {warMenu(core)},
        "城市" to {cityMenu(core)},
        "[green]银行" to {bankMenu(core)},
        "领主" to {lordMenu(core)}
    )
    this += listOf(
        "取消" to {}
    )
    }
}
suspend fun Player.lordMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]领主页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]你当前拥有金币[#${team().color}]${Iconc.blockCliff}${coins()}
            [cyan]升级属性！
        """.trimIndent()
    ) {
        fun Float.getRulesCost(cost1: Int, cost2: Int = 1): Float {
            return (this.pow(cost2) * cost1).pow(2)
        }
        fun Int.getRulesCost(cost: Int): Int {
            return (this * cost).toFloat().pow(2).toInt()
        }

        if (unitType().level() > 0) {
            this += listOf(
                "单位上限\n${unitCap()}->${unitCap() + 4}\n[#${team().color}]${Iconc.blockCliff}${unitCap().getRulesCost(2)}" to {
                    if (coins() >= unitCap().getRulesCost(2)) {
                        removeCoin(unitCap().getRulesCost(2))
                        unitCap(unitCap() + 4)
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                }
            )
        }
            this += listOf(
                "建筑血量\n${team().rules().blockHealthMultiplier.format()}->${(team().rules().blockHealthMultiplier + 0.05f).format()}\n[#${team().color}]${Iconc.blockCliff}${team().rules().blockHealthMultiplier.getRulesCost(40).toInt()}" to {
                    if (coins() >= team().rules().blockHealthMultiplier.getRulesCost(40)) {
                        removeCoin(team().rules().blockHealthMultiplier.getRulesCost(40).toInt())
                        team().rules().blockHealthMultiplier += 0.05f
                        Call.setRules(state.rules)
                        Call.sendMessage("[white]$name [#${team().color}]购买了建筑血量(${(team().rules().blockHealthMultiplier - 0.05f).format()} -> ${team().rules().blockHealthMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                },
                "建筑攻击\n${team().rules().blockDamageMultiplier.format()}->${(team().rules().blockDamageMultiplier + 0.05f).format()}\n[#${team().color}]${Iconc.blockCliff}${team().rules().blockDamageMultiplier.getRulesCost(40).toInt()}" to {
                    if (coins() >= team().rules().blockDamageMultiplier.getRulesCost(40)) {
                        removeCoin(team().rules().blockDamageMultiplier.getRulesCost(40).toInt())
                        team().rules().blockDamageMultiplier += 0.05f
                        Call.setRules(state.rules)
                        Call.sendMessage("[white]$name [#${team().color}]购买了建筑攻击(${(team().rules().blockDamageMultiplier - 0.05f).format()} -> ${team().rules().blockDamageMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                }
            )
            this += listOf(
                "建筑速度\n${team().rules().buildSpeedMultiplier.format()}->${(team().rules().buildSpeedMultiplier + 0.05f).format()}\n[#${team().color}]${Iconc.blockCliff}${team().rules().buildSpeedMultiplier.getRulesCost(25).toInt()}" to {
                    if (coins() >= team().rules().buildSpeedMultiplier.getRulesCost(25)) {
                        removeCoin(team().rules().buildSpeedMultiplier.getRulesCost(25).toInt())
                        team().rules().buildSpeedMultiplier += 0.05f
                        Call.setRules(state.rules)
                        Call.sendMessage("[white]$name [#${team().color}]购买了建筑速度(${(team().rules().buildSpeedMultiplier - 0.05f).format()} -> ${team().rules().buildSpeedMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                },
                "单位攻击\n${team().rules().unitDamageMultiplier.format()}->${(team().rules().unitDamageMultiplier + 0.05f).format()}\n[#${team().color}]${Iconc.blockCliff}${team().rules().unitDamageMultiplier.getRulesCost(45).toInt()}" to {
                    if (coins() >= team().rules().unitDamageMultiplier.getRulesCost(45)) {
                        removeCoin(team().rules().unitDamageMultiplier.getRulesCost(45).toInt())
                        team().rules().unitDamageMultiplier += 0.05f
                        Call.setRules(state.rules)
                        Call.sendMessage("[white]$name [#${team().color}]购买了单位攻击(${(team().rules().unitDamageMultiplier - 0.05f).format()} -> ${team().rules().unitDamageMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                }
            )
        this += listOf(
            "单位花费\n${team().techData().unitCostMultiplier.format()}->${(team().techData().unitCostMultiplier - 0.05f).format()}\n[#${team().color}]${Iconc.blockCliff}${(2f - team().techData().unitCostMultiplier).getRulesCost(200, 2).format()}" to {
                if (coins() >= (2f - team().techData().unitCostMultiplier).getRulesCost(200, 2)) {
                    removeCoin((2f - team().techData().unitCostMultiplier).getRulesCost(200, 2).toInt())
                    team().techData().unitCostMultiplier -= 0.05f
                    Call.sendMessage("[white]$name [#${team().color}]购买了单位花费(${(team().techData().unitCostMultiplier + 0.05f).format()} -> ${team().techData().unitCostMultiplier.format()}}")
                    lordMenu(core)
                } else {
                    sendMessage("[red]金钱不足！")
                }
            }
        )
        this += listOf(
            "[green]属性" to {lordMenu(core)},
            "科技" to {techMenu(core)}
        )
        this += listOf(
            "军团" to {warMenu(core)},
            "城市" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "[green]领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
    }
}

suspend fun Player.techMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]科技页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]队伍当前拥有科技经验[sky]${Iconc.teamSharded}${team().techData().exp.format(1)}
            [cyan]研究科技！当前科技等级：[#${team().color}] ${team().techData().level} [cyan]科技点： [#${team().color}] ${team().techData().techPoint}
        """.trimIndent()
    ) {
        this += listOf(if (team().techData().exp >= team().techData().techCost()){
            "升级队伍科技等级\n[sky]${Iconc.teamSharded}${team().techData().techCost()}" to {
                if (team().techData().exp >= team().techData().techCost()){
                    team().techData().exp -= team().techData().techCost()
                    team().techData().level++
                    team().techData().techPoint++
                    Call.sendMessage("[white]$name [#${team().color}]获得了新的灵感！队伍科技等级(${team().techData().level - 1} -> ${team().techData().level})")
                    techMenu(core)
                } else {
                    sendMessage("[red]金钱不足！")
                }
            }

        } else {
            "[lightgray]需要${Iconc.teamSharded}${team().techData().techCost()}\n来升级队伍科技等级!" to {
                techMenu(core)
            }
        })
        this += listOf(
            "[cyan]效果弹头 [white]${buildString { team().techData().bulletStatusEffect.keys.forEach { append(it.emoji()) } }}" to {
                sendMessage(buildString {
                    appendLine("[cyan]单位攻击有概率射出附带负面buff的子弹")
                    appendLine("[white]目前的效果弹头：${buildString { team().techData().bulletStatusEffect.keys.forEach { append(it.emoji()) } }}")
                    append("[yellow]目前触发概率${(team().techData().bulletStatusEffectProbability * 100f).format()}%")
                })
            }
        )
        if(team().techData().bulletStatusEffect.keys.any { it != StatusEffects.none})
            this += listOf("效果弹头触发概率\n${(team().techData().bulletStatusEffectProbability * 100f).format()}% -> ${((team().techData().bulletStatusEffectProbability + 0.05f) * 100f).format()}%" to {
                if (team().techData().techPoint > 0) {
                    team().techData().techPoint--
                    team().techData().bulletStatusEffectProbability += 0.05f
                    Call.sendMessage("[white]$name [#${team().color}]改良了队伍的效果弹头！(${((team().techData().bulletStatusEffectProbability - 0.05f) * 100f).format()}% -> ${(team().techData().bulletStatusEffectProbability * 100f).format()}%)")
                    techMenu(core)
                } else {
                    sendMessage("[red]科技点不足")
                }
        })
        this += listOf(
            "[cyan]兵营效果 [white]${buildString { team().techData().unitStatusEffect.keys.forEach { append(it.emoji()) } }}" to {
                sendMessage(buildString {
                    appendLine("[cyan]招募单位有概率附带buff")
                    appendLine("[white]目前的兵营效果：${buildString { team().techData().unitStatusEffect.keys.forEach { append(it.emoji()) } }}")
                    append("目前触发概率${(team().techData().unitStatusEffectProbability * 100f).format()}%")
                })
            }
        )
        if(team().techData().unitStatusEffect.keys.any { it != StatusEffects.none})
            this += listOf("兵营效果触发概率\n${(team().techData().unitStatusEffectProbability * 100f).format()}% -> ${((team().techData().unitStatusEffectProbability + 0.1f) * 100f).format()}%" to {
                if (team().techData().techPoint > 0) {
                    team().techData().techPoint--
                    team().techData().unitStatusEffectProbability += 0.25f
                    Call.sendMessage("[white]$name [#${team().color}]提高了兵营效果触发概率！(${((team().techData().unitStatusEffectProbability - 0.1f) * 100f).format()}% -> ${(team().techData().unitStatusEffectProbability * 100f).format()}%)")
                    techMenu(core)
                } else {
                    sendMessage("[red]科技点不足")
                }
            })
        this += listOf(
            "[cyan]领主增幅" to {
                sendMessage(buildString {
                    appendLine("[cyan]领主增幅 每次领主降临自动加强")
                    appendLine("[white]目前的领主增幅：")
                    appendLine("[#${team().color}]领主生命倍率：[white]${(team().techData().lordUnitHealthMultiplier * 100f).format()}%")
                    appendLine("[lightgray]会导致不同步，但是血量确实加了")
                    appendLine("[#${team().color}]领主降临时间倍率：[white]${(team().techData().lordUnitSpawnTimeMultiplier * 100f).format()}%")
                    appendLine("[#${team().color}]领主降临后爆炸倍率：[white]${(team().techData().lordUnitExplosionMultiplier * 100f).format()}%")
                    appendLine("[#${team().color}]领主降临后临时无敌时间：[white]${(team().techData().lordUnitSpawnInvincibleTime).format(1)}s")
                    appendLine("[#${team().color}]领主降临花费倍率：[white]${(team().techData().lordUnitCostMultiplier * 100f).format()}%")
                })
            }
        )
        val statusEffectListB = mapOf(
            StatusEffects.wet to 1.25f * 60f,
            StatusEffects.corroded to 5f * 60f,
            StatusEffects.shocked to 1f * 60f,
            StatusEffects.blasted to 1f * 60f,
            StatusEffects.tarred to 5f * 60f,
            StatusEffects.freezing to 1.25f * 60f
        )
        fun bulletEffectInfo(type: StatusEffect): Pair<String, suspend () -> Unit> {
            return if(type in team().techData().bulletStatusEffect.keys.toList())
                "[cyan]获得过的科技" to suspend {
                    techMenu(core)
                }
            else "效果弹头\n${type.emoji()}" to suspend {
                if (team().techData().techPoint > 0 && type !in team().techData().bulletStatusEffect.keys.toList()) {
                    team().techData().techPoint--
                    team().techData().bulletStatusEffect[type] = statusEffectListB[type] ?: (5 * 60f)
                    Call.sendMessage("[white]$name [#${team().color}]给队伍加装了新的效果弹头！(${type.emoji()})")
                    techMenu(core)
                }
            }
        }
        val statusEffectListU = listOf(
            StatusEffects.boss,
            StatusEffects.overclock,
            StatusEffects.overdrive,
        )
        fun unitEffectInfo(type: StatusEffect): Pair<String, suspend () -> Unit> {
            return if(type in team().techData().unitStatusEffect.keys.toList())
                    "[cyan]获得过的科技" to suspend {
                        techMenu(core)
                    }
            else "兵营效果\n${type.emoji()}" to suspend {
                    if (team().techData().techPoint > 0 && type !in team().techData().unitStatusEffect.keys.toList()) {
                        team().techData().techPoint--
                        team().techData().unitStatusEffect[type] = 999999f
                        Call.sendMessage("[white]$name [#${team().color}]给队伍添加了新的兵营效果！(${type.emoji()})")
                        techMenu(core)
                    }
            }
        }
        val array = arrayOf<List<List<Pair<String, suspend () -> Unit>>>>(
            buildList{ statusEffectListB.keys.forEach {
                    add(buildList { add(bulletEffectInfo(it)) })
                }},
            buildList { statusEffectListU.forEach {
                    add(buildList { add(unitEffectInfo(it)) })
                }},
            listOf(buildList {
                    add("roll与升级\n${(team().techData().upgradeAndRollMultiplier * 100f).format()}% -> ${(team().techData().upgradeAndRollMultiplier * 100f * 0.75f).format()}" to suspend {
                        if (team().techData().techPoint > 0) {
                            team().techData().techPoint--
                            val last = team().techData().upgradeAndRollMultiplier
                            team().techData().upgradeAndRollMultiplier *= 0.75f
                            Call.sendMessage("[white]$name [#${team().color}]减少了队伍升级和roll兵花费！(${(last * 100f).format()}% -> ${(team().techData().upgradeAndRollMultiplier * 100f).format()}%)")
                            //?
                            techMenu(core)
                        }
                    })
            })
        )
        if(team().techData().techPoint > 0) {
            if (team().techData().exp >= (team().techData().techCost() * 0.25f).toInt()) {
                this += listOf(
                    "重置科技选择选项\n[sky]${Iconc.teamSharded}${
                        (team().techData().techCost() * 0.25f).toInt()
                    }" to {
                        if (team().techData().exp >= (team().techData().techCost() * 0.25f).toInt()) {
                            team().techData().exp -= (team().techData().techCost() * 0.25f).toInt()
                            team().techData().randomSeed = Random.nextInt(0, 9999)
                            Call.sendMessage(
                                "[white]$name [#${team().color}]花费了[sky]${Iconc.teamSharded}${
                                    (team().techData().techCost() * 0.25f).toInt()
                                }[#${team().color}]重置了科技选择！"
                            )
                            techMenu(core)
                        }
                    })
            } else {
                this += listOf(
                    "[lightgray]重置科技选择选项\n${Iconc.teamSharded}${
                        (team().techData().techCost() * 0.25f).toInt()
                    }" to {
                        techMenu(core)
                    })
            }
            add(
                buildList {
                    repeat(2) { j ->
                        val seed =
                            Random(team().techData().randomSeed * (team().id * 1000 + j) * (team().techData().level - team().techData().techPoint))
                        add(array.random(seed).random(seed).random(seed))
                    }
                }
            )
            add(
                buildList {
                    repeat(2) { j ->
                        val seed =
                            Random(team().techData().randomSeed * (team().id * 10000 + j) * (team().techData().level - team().techData().techPoint))
                        add(array.random(seed).random(seed).random(seed))
                    }
                }.reversed()
            )
        }
        this += listOf(
            "属性" to {lordMenu(core)},
            "[green]科技" to {techMenu(core)}
        )
        this += listOf(
            "军团" to {warMenu(core)},
            "城市" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "[green]领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
        if (admin && Groups.player.size() <= 5){
            this += listOf(
                "[red]<ADMIN>加10000000科技经验" to { team().techData().exp += 10000000  }
            )
        }
    }
}

class DisruptMissileAi: MissileAI() {

    override fun updateWeapons(){
    }

    private fun circle2(target: Position?, circleLength: Float, speed: Float) {
        if (target == null) return
        vec.set(target).sub(unit)
        if (vec.len() < circleLength) {
            vec.rotate(-(circleLength - vec.len()) / circleLength * 180f)
        }
        vec.setLength(speed)
        unit.moveAt(vec)
    }

     override fun updateMovement() {
         unloadPayloads()

         val time = if (unit is TimedKillc) (unit as TimedKillc).time() else 1000000f

         val dst = Mathf.dst(shooter.x, shooter.y, shooter.aimX,  shooter.aimY) * 1.25f
         val uDst = dst - Mathf.dst(unit.x, unit.y, shooter.aimX,  shooter.aimY)

         val circle = Random(unit.id).nextBoolean()

         if (shooter.isShooting && unit.within(shooter.aimX, shooter.aimY, dst / 2.2f)){
             unit.apply(StatusEffects.boss, 999999f)

             if (circle)
                circle(Vec2(shooter.aimX, shooter.aimY), dst * max(Random.nextFloat(), 0.25f), unit.speed() * min(max((uDst / 408), 0.2f), 1.5f))
             else
                 circle2(Vec2(shooter.aimX, shooter.aimY), dst * max(Random.nextFloat(), 0.25f), unit.speed() * min(max((uDst / 408), 0.2f), 1.5f))
             unit.rotation(Angles.angle(unit.x, unit.y, shooter.aimX, shooter.aimY))
         }else {
             if (time >= unit.type.homingDelay && shooter != null) {
                 unit.lookAt(shooter.aimX, shooter.aimY)
             }

             unit.unapply(StatusEffects.boss)

             unit.moveAt(
                 vec.trns(
                     unit.rotation, if (unit.type.missileAccelTime <= 0f) unit.speed() else Mathf.pow(
                         (time / unit.type.missileAccelTime).coerceAtMost(1f), 2f
                     ) * unit.speed()
                 )
             )
         }
         val build = unit.buildOn()

         if (build != null && build.team != unit.team && (build == target || !build.block.underBullets)) {
             unit.kill()
         }
     }
}

fun TeamData.pads(): List<Building> {
    return Groups.build.filter { it.team == team && it.block is LaunchPad }
}

onEnable{
    //反正不影响同步 我也不会用ct改 (((
    contextScript<coreMindustry.UtilMapRule>().registerMapRule(
        UnitTypes.disrupt.weapons.get(0).bullet.spawnUnit::controller
    ) { Func<mindustry.gen.Unit, UnitController> { DisruptMissileAi() } }
    contextScript<coreMindustry.UtilMapRule>().registerMapRule(
        UnitTypes.disrupt.weapons.get(0).bullet.spawnUnit.weapons.get(0).bullet::spawnUnit
    ) { UnitTypes.quell.weapons.get(0).bullet.spawnUnit }
    //contextScript<coreMindustry.ContentsTweaker>().addPatch("LordOfWar", dataDirectory.child("contents-patch").child("14668.json").readString())
    contextScript<coreMindustry.ContentsTweaker>().addPatch("Lord Of War",
           "{\n" +
                   "  \"block\": {\n" +
                   "    \"launch-pad\": {\n" +
                   "      \"health\": 5000,\n" +
                   "      \"requirements\": [\n" +
                   "        \"copper/1000\",\n" +
                   "        \"lead/800\"\n" +
                   "      ],\n" +
                   "      \"solid\": false\n" +
                   "    },\n" +
                   "    \"core-shard\": {\n" +
                   "      \"health\": 2500,\n" +
                   "      \"armor\": 5\n" +
                   "    },\n" +
                   "    \"core-foundation\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 4500,\n" +
                   "      \"armor\": 10\n" +
                   "    },\n" +
                   "    \"core-bastion\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 8000,\n" +
                   "      \"armor\": 15\n" +
                   "    },\n" +
                   "    \"core-nucleus\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 12000,\n" +
                   "      \"armor\": 20\n" +
                   "    },\n" +
                   "    \"core-citadel\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 32000,\n" +
                   "      \"armor\": 25\n" +
                   "    },\n" +
                   "    \"core-acropolis\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 80000,\n" +
                   "      \"armor\": 30\n" +
                   "    }\n" +
                   "  },\n" +
                   "  \"unit\": {\n" +
                   "    \"dagger\": {\n" +
                   "      \"armor\": 0\n" +
                   "    },\n" +
                   "    \"nova\": {\n" +
                   "      \"armor\": 0,\n" +
                   "      \"weapons.0.bullet.healPercent\": 0,\n" +
                   "      \"weapons.0.bullet.healAmount\": 50\n" +
                   "    },\n" +
                   "    \"merui\": {\n" +
                   "      \"health\": 80,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 18,\n" +
                   "      \"armor\": 0\n" +
                   "    },\n" +
                   "    \"elude\": {\n" +
                   "      \"health\": 120,\n" +
                   "      \"weapons.0.bullet.damage\": 7,\n" +
                   "      \"armor\": 0\n" +
                   "    },\n" +
                   "    \"stell\": {\n" +
                   "      \"health\": 120,\n" +
                   "      \"weapons.0.bullet.damage\": 18,\n" +
                   "      \"armor\": 4\n" +
                   "    },\n" +
                   "    \"pulsar\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 9,\n" +
                   "      \"weapons.0.bullet.healPercent\": 0,\n" +
                   "      \"weapons.0.bullet.healAmount\": 35,\n" +
                   "      \"weapons.0.bullet.lightningType.healPercent\": 0,\n" +
                   "      \"weapons.0.bullet.lightningType.healAmount\": 21,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"poly\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 12,\n" +
                   "      \"weapons.0.bullet.healPercent\": 0,\n" +
                   "      \"weapons.0.bullet.healAmount\": 55,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"atrax\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 18,\n" +
                   "      \"weapons.0.bullet.collidesAir\": true,\n" +
                   "      \"targetAir\": true,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"avert\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 14,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"locus\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 12,\n" +
                   "      \"armor\": 8\n" +
                   "    },\n" +
                   "    \"mace\": {\n" +
                   "      \"health\": 620,\n" +
                   "      \"weapons.0.bullet.damage\": 38,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"mega\": {\n" +
                   "      \"health\": 320,\n" +
                   "      \"weapons.0.bullet.damage\": 12,\n" +
                   "      \"weapons.2.bullet.damage\": 6,\n" +
                   "      \"weapons.0.bullet.healPercent\": 0,\n" +
                   "      \"weapons.0.bullet.healAmount\": 35,\n" +
                   "      \"weapons.2.bullet.healPercent\": 0,\n" +
                   "      \"weapons.2.bullet.healAmount\": 15,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"cleroi\": {\n" +
                   "      \"health\": 460,\n" +
                   "      \"weapons.2.bullet.damage\": 12,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"zenith\": {\n" +
                   "      \"health\": 420,\n" +
                   "      \"weapons.0.bullet.damage\": 32,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"precept\": {\n" +
                   "      \"health\": 840,\n" +
                   "      \"weapons.0.bullet.damage\": 36,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 20,\n" +
                   "      \"weapons.0.bullet.fragBullet.damage\": 12,\n" +
                   "      \"armor\": 15\n" +
                   "    },\n" +
                   "    \"spiroct\": {\n" +
                   "      \"health\": 460,\n" +
                   "      \"weapons.0.bullet.damage\": 37,\n" +
                   "      \"weapons.0.bullet.sapStrength\": 0,\n" +
                   "      \"weapons.2.bullet.damage\": 33,\n" +
                   "      \"weapons.2.bullet.sapStrength\": 0,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"cyerce\": {\n" +
                   "      \"health\": 860,\n" +
                   "      \"flying\": true,\n" +
                   "      \"weapons.0.bullet.maxRange\": 65,\n" +
                   "      \"weapons.0.repairSpeed\": 0.05,\n" +
                   "      \"weapons.1.repairSpeed\": 0.05,\n" +
                   "      \"weapons.2.bullet.fragBullet.healPercent\": 0,\n" +
                   "      \"weapons.2.bullet.fragBullet.healAmount\": 28,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"anthicus\": {\n" +
                   "      \"health\": 880,\n" +
                   "      \"weapons.0.bullet.spawnUnit.weapons.0.bullet.splashDamage\": 80,\n" +
                   "      \"weapons.0.shootStatus\": \"slow\",\n" +
                   "      \"weapons.0.shootStatusDuration\": 131,\n" +
                   "      \"weapons.1.shootStatus\": \"slow\",\n" +
                   "      \"weapons.1.shootStatusDuration\": 131,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"antumbra\": {\n" +
                   "      \"health\": 820,\n" +
                   "      \"weapons.0.bullet.damage\": 11,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 23,\n" +
                   "      \"weapons.5.bullet.damage\": 25,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"vanquish\": {\n" +
                   "      \"health\": 1560,\n" +
                   "      \"weapons.0.bullet.damage\": 85,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 35,\n" +
                   "      \"armor\": 22\n" +
                   "    },\n" +
                   "    \"arkyid\": {\n" +
                   "      \"health\": 2140,\n" +
                   "      \"weapons.0.bullet.sapStrength\": 0,\n" +
                   "      \"armor\": 18\n" +
                   "    },\n" +
                   "    \"vela\": {\n" +
                   "      \"health\": 1860,\n" +
                   "      \"armor\": 18,\n" +
                   "      \"weapons.0.bullet.healPercent\": 0,\n" +
                   "      \"weapons.0.bullet.healAmount\": 55\n" +
                   "    },\n" +
                   "    \"tecta\": {\n" +
                   "      \"health\": 1260,\n" +
                   "      \"armor\": 18\n" +
                   "    },\n" +
                   "    \"sei\": {\n" +
                   "      \"health\": 1540,\n" +
                   "      \"weapons.0.bullet.damage\": 19,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 21,\n" +
                   "      \"weapons.2.bullet.damage\": 32,\n" +
                   "      \"flying\": true,\n" +
                   "      \"armor\": 18\n" +
                   "    },\n" +
                   "    \"scepter\": {\n" +
                   "      \"health\": 2680,\n" +
                   "      \"weapons.0.shoot.shots\": 2,\n" +
                   "      \"weapons.0.bullet.damage\": 65,\n" +
                   "      \"weapons.0.bullet.lightningType.lightningDamage\": 30,\n" +
                   "      \"weapons.1.shoot.shots\": 2,\n" +
                   "      \"weapons.2.shoot.shots\": 3,\n" +
                   "      \"weapons.2.bullet.damage\": 24,\n" +
                   "      \"weapons.3.shoot.shots\": 3,\n" +
                   "      \"weapons.1.shoot.shotDelay\": 2,\n" +
                   "      \"weapons.4.shoot.shots\": 3,\n" +
                   "      \"weapons.5.shoot.shots\": 3,\n" +
                   "      \"weapons.5.shoot.shotDelay\": 2,\n" +
                   "      \"armor\": 26\n" +
                   "    },\n" +
                   "    \"toxopid\": {\n" +
                   "      \"health\": 9800,\n" +
                   "      \"weapons.0.shoot.shots\": 3,\n" +
                   "      \"weapons.1.shoot.shots\": 3,\n" +
                   "      \"weapons.0.bullet.damage\": 150,\n" +
                   "      \"weapons.2.bullet.collidesAir\": true,\n" +
                   "      \"weapons.2.bullet.splashDamage\": 25,\n" +
                   "      \"weapons.2.bullet.fragBullet.collidesAir\": true,\n" +
                   "      \"weapons.2.bullet.fragBullet.damage\": 15,\n" +
                   "      \"abilities.+=\": [\n" +
                   "        {\n" +
                   "          \"type\": \"RegenAbility\",\n" +
                   "          \"percentAmount\": 0.027\n" +
                   "        },\n" +
                   "        {\n" +
                   "          \"type\": \"SuppressionFieldAbility\",\n" +
                   "          \"range\": 520\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"aegires\": {\n" +
                   "      \"health\": 7800,\n" +
                   "      \"abilities.0\": {\n" +
                   "        \"damage\": 80,\n" +
                   "        \"maxTargets\": 80,\n" +
                   "        \"healPercent\": 4\n" +
                   "      },\n" +
                   "      \"flying\": true,\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"collaris\": {\n" +
                   "      \"health\": 8400,\n" +
                   "      \"targetAir\": true,\n" +
                   "      \"weapons.0.bullet.collidesAir\": true,\n" +
                   "      \"weapons.0.bullet.fragBullet.collidesAir\": true,\n" +
                   "      \"weapons.0.bullet.damage\": 150,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 30,\n" +
                   "      \"weapons.0.bullet.fragBullet.damage\": 23,\n" +
                   "      \"weapons.0.bullet.fragBullet.splashDamage\": 16,\n" +
                   "      \"abilities.+=\": [{\n" +
                   "        \"type\": \"UnitSpawnAbility\",\n" +
                   "        \"spawnTime\": 900,\n" +
                   "        \"unit\": \"flare\",\n" +
                   "        \"spawnX\": 0,\n" +
                   "        \"spawnY\": -8\n" +
                   "      }\n" +
                   "      ],\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"flare\": {\n" +
                   "      \"health\": 10,\n" +
                   "      \"fogRadius\": 64,\n" +
                   "      \"speed\": 5.2\n" +
                   "    },\n" +
                   "    \"eclipse\": {\n" +
                   "      \"health\": 10600,\n" +
                   "      \"abilities.+=\": [\n" +
                   "        {\n" +
                   "          \"type\": \"StatusFieldAbility\",\n" +
                   "          \"duration\": 240,\n" +
                   "          \"effect\": \"overclock\",\n" +
                   "          \"reload\": 120,\n" +
                   "          \"range\": 240\n" +
                   "        },\n" +
                   "        {\n" +
                   "          \"type\": \"ShieldRegenFieldAbility\",\n" +
                   "          \"amount\": 120,\n" +
                   "          \"max\": 360,\n" +
                   "          \"reload\": 240,\n" +
                   "          \"range\": 240\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"conquer\": {\n" +
                   "      \"health\": 12800,\n" +
                   "      \"flying\": true,\n" +
                   "      \"abilities.+=\": [\n" +
                   "        {\n" +
                   "          \"type\": \"ForceFieldAbility\",\n" +
                   "          \"radius\": 200,\n" +
                   "          \"regen\": 4.2,\n" +
                   "          \"max\": 9600,\n" +
                   "          \"cooldown\": 960\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"armor\": 42\n" +
                   "    },\n" +
                   "    \"disrupt\": {\n" +
                   "      \"health\": 8800,\n" +
                   "      \"flying\": true,\n" +
                   "      \"abilities.0.range\": 720,\n" +
                   "      \"weapons.0.reload\": 3200,\n" +
                   "      \"weapons.0.shoot.shots\": 18,\n" +
                   "      \"weapons.0.shoot.shotDelay\": 8,\n" +
                   "      \"weapons.0.shoot.firstShotDelay\": 12,\n" +
                   "      \"weapons.0.inaccuracy\": 120,\n" +
                   "      \"weapons.0.shootStatus\": \"slow\",\n" +
                   "      \"weapons.0.shootStatusDuration\": 1601,\n" +
                   "      \"weapons.1.reload\": 3200,\n" +
                   "      \"weapons.1.shoot.shots\": 18,\n" +
                   "      \"weapons.1.shoot.shotDelay\": 8,\n" +
                   "      \"weapons.1.shoot.firstShotDelay\": 12,\n" +
                   "      \"weapons.1.inaccuracy\": 120,\n" +
                   "      \"weapons.1.shootStatus\": \"slow\",\n" +
                   "      \"weapons.1.shootStatusDuration\": 1601,\n" +
                   "      \"weapons.0.bullet.spawnUnit.lifetime\": 350,\n" +
                   "      \"weapons.0.bullet.spawnUnit.health\": 320,\n" +
                   "      \"weapons.0.bullet.spawnUnit.armor\": 6,\n" +
                   "      \"weapons.0.bullet.spawnUnit.rotateSpeed\": 0.8,\n" +
                   "      \"weapons.0.bullet.spawnUnit.speed\": 2.8,\n" +
                   "      \"weapons.0.bullet.spawnUnit.weapons.0.shoot.shots\": 2,\n" +
                   "      \"weapons.0.bullet.spawnUnit.weapons.0.inaccuracy\": 45,\n" +
                   "      \"weapons.0.bullet.spawnUnit.fogRadius\": 9,\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"quell.weapons.0.bullet.spawnUnit\": {\n" +
                   "      \"rotateSpeed\": 0,\n" +
                   "      \"health\": 20,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 240,\n" +
                   "      \"weapons.0.bullet.splashDamageRadius\": 45,\n" +
                   "      \"weapons.0.bullet.buildingDamageMultiplier\": 0.4,\n" +
                   "      \"speed\": 6,\n" +
                   "      \"fogRadius\": 6\n" +
                   "    }\n" +
                   "  }\n" +
                   "}"
    )
    launch(Dispatchers.game){
        state.teams.getActive().forEach {
            it.cores.forEach { c ->
                c.maxHealth = c.block.health.toFloat()
                c.health = c.maxHealth
            }
        }
        state.rules.apply {
            modeName = "LordOfWar"
            unitCap = 99
            revealedBlocks.add(Blocks.launchPad)
        }
    }
    loop(Dispatchers.game){
        var cores = 0f
        state.teams.getActive().forEach {
            cores += it.cores.size
        }
        state.teams.getActive().forEach { it ->
            val rate = it.team.coins() * 0.001f * (0.5f - it.cores.size / cores) * 2f
            it.team.addCoin(rate.toInt())
            if (it.team.coins() < 0) it.team.setCoin(0)
            it.cores.forEach { c ->
                var amount = c.level() * max((Time.timeSinceMillis(startTime) / 1000 / 60 / 3 * c.cityData().cityType.coinsMultiplier).toInt(), 1)
                val baseAmount = c.level() * max((Time.timeSinceMillis(startTime) / 1000 / 60 / 3).toInt(), 1)
                Groups.player.filter { p -> p.uuid() == c.lord() }.forEach { p ->
                    p.addCoin(amount / 2, true)
                    amount -= amount / 2
                }
                it.team.techData().exp += c.level() * c.cityData().cityType.researchMultiplier
                c.addCoin(amount)
                val allyText = buildString {
                    append("[#${c.team.color}]${Iconc.blockCliff}${c.coins()} ")
                    repeat((c.health / c.maxHealth * 10).toInt()) {
                        append("|")
                    }
                    appendLine("")
                    append("[white]${amount.levelText()}[#${c.team.color}]${Iconc.blockCliff}$amount($baseAmount)/s")
                    appendLine(" [white]| [sky]${Iconc.teamSharded}${(c.level() * c.cityData().cityType.researchMultiplier).format(1)}/s")
                    Groups.player.filter { p -> p.uuid() == c.lord() }.forEach {
                        appendLine("[white]${it.name}")
                    }
                    append("[white]")
                    append(c.levelText())
                    append("[white] | ")
                    append(c.cityData().cityType.name)
                }
                val enemyText = buildString {
                    append("[#${c.team.color}]${Iconc.blockCliff}${"?".repeat(c.coins().toString().length)} ")
                    repeat((c.health / c.maxHealth * 10).toInt()) {
                        append("|")
                    }
                    appendLine("")
                    appendLine("[white]${amount.levelText()}[#${c.team.color}]$amount/s")
                    append("[white]")
                    append(c.levelText(true))
                }
                    Groups.player.filter { p ->
                               (p.within(c.x, c.y, 60f * 8f)
                            || (world.tileWorld(p.mouseX, p.mouseY) != null
                             && world.tileWorld(p.mouseX, p.mouseY).within(c.x, c.y, 30f * 8f)))
                             && fogControl.isVisible(p.team(), c.x, c.y)
                    }.forEach { p ->
                        Call.labelReliable(p.con, if (p.team() == it.team) allyText else enemyText, 1.013f, c.x, c.y)
                    }
                Units.nearby(null, c.x, c.y, 20 * 8f) { u ->
                    if (u.team == c.team && u.health < u.maxHealth) {
                        u.health += u.maxHealth / 100 * c.cityData().cityType.healMultiplier * c.level() / 6
                        u.clampHealth()
                        Call.transferItemEffect(Items.plastanium, c.x, c.y, u)
                    }
                }
            }
        }
        delay(1000)
    }
    loop(Dispatchers.game) {
        state.teams.getActive().forEach { t ->
            val pads = t.pads()
            pads.forEach {
                val pad = (it as LaunchPad.LaunchPadBuild)
                val allyText = buildString {
                    appendLine("[#${t.team.color}]${if(pad.padData().name != "") pad.padData().name else "(${pad.tileX()},${pad.tileY()})"}")
                    val targetPad = pad.padData().targetPad
                    appendLine("[#${t.team.color}]传送目标 ${targetPad?.padData()?.name ?: ""}")
                    if (targetPad == null) {
                        append("[lightgray]未配置")
                    } else if (!targetPad.isValid){
                        append("[lightgray]失效")
                    } else {
                        append("[white]${Iconc.blockLaunchPad}(${targetPad.tileX()},${targetPad.tileY()})${Iconc.blockLaunchPad}")
                    }
                }
                val enemyText = buildString {
                    appendLine("[#${t.team.color}]传送发射台")
                    appendLine("${Iconc.blockLaunchPad}[#${t.team.color}][white](${pad.tileX()},${pad.tileY()})[#${t.team.color}]${Iconc.blockLaunchPad}")
                    appendLine("[#${t.team.color}]传送目标")
                    append("[lightgray]未知")
                }
                Groups.player.filter { p ->
                    (p.within(pad.x, pad.y, 60f * 8f)
                            || (world.tileWorld(p.mouseX, p.mouseY) != null
                            && world.tileWorld(p.mouseX, p.mouseY).within(pad.x, pad.y, 30f * 8f)))
                            && fogControl.isVisible(p.team(), pad.x, pad.y)
                }.forEach { p ->
                    Call.labelReliable(p.con, if (p.team() == it.team) allyText else enemyText, 1.013f, pad.x, pad.y)
                }
            }
        }
        delay(1000)
    }
    loop(Dispatchers.game){
        Groups.player.forEach{
            val text = buildString {
                appendLine("[#${it.team().color}]金币:${Iconc.blockCliff}${it.coins()}")
                appendLine("军团等级:${it.unitType().level()}")
                appendLine("单位上限:${Groups.unit.filter { u -> u.owner() == it.uuid() }.size}/${it.unitCap()}")
                if (!it.checkCooldown())
                    append("[red]收取资源冷却时间:${cooldown[it.uuid()] / 1000 - Time.timeSinceMillis(startTime) / 1000}s")
                else
                    append("[green]收取资源冷却完毕")
                if (it.unitType().level() == 5)
                    if (!it.checkLordCooldown())
                        append("\n[red]领主降临冷却时间:${playerLordCooldown[it.uuid()] / 1000 - Time.timeSinceMillis(startTime) / 1000}s")
                    else
                        append("\n[green]领主降临冷却完毕")
                if (it.unitType().level() > 0) {
                    appendLine("[white]")
                    append("军团类型:${it.unitType()?.emoji()}")
                    if (it.unitType().level() >= 5 && it.lordUnitType() != null)
                        append("领主类型:${it.lordUnitType()?.emoji()}")
                }
            }
            Call.setHudText(it.con,text)
        }
        delay(100)
    }
}

suspend fun Player.launchPadMenu(pad: LaunchPad.LaunchPadBuild) {
    val pads = team().data().pads()
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]发射台页面 [#${pad.team.color}]${pad.padData().name}\n[cyan]发射台总数[yellow]${pads.size}",
        """
            [cyan]当前拥有金币[#${team().color}]${Iconc.blockCliff}${coins()}
            [cyan]当前科技等级[#${team().color}]${team().techData().level}
            [cyan]各种发射台连锁效果！
        """.trimIndent()
    ) {
        val targetPad = pad.padData().targetPad
        this += listOf(
            if (pads.size >= 2) {
                if (targetPad != null) {
                    if (targetPad.isValid) {
                        "[cyan]目标已经选定！\n[white]${Iconc.blockLaunchPad}(${targetPad.tileX()},${targetPad.tileY()})${Iconc.blockLaunchPad} [#${targetPad.team.color}]${targetPad.padData().name}"
                    } else {
                        "[lightgray]目标失效！点击重新选择目标"
                    }
                } else {
                    "[lightgray]未配置！点击重新选择目标"
                } to {
                    menu.sendMenuBuilder<Unit>(
                        this@launchPadMenu, 30_000, "[green]发射台页面\n[cyan]发射台总数[yellow]${pads.size}",
                        """
                           [cyan]选择发射台目标
                        """.trimIndent()
                    ) {
                        pads.filter{ it != pad }.forEach {
                            add(listOf("[white]${Iconc.blockLaunchPad}(${it.tileX()},${it.tileY()})${Iconc.blockLaunchPad} [#${it.team.color}]${(it as LaunchPad.LaunchPadBuild).padData().name}" to {
                                pad.padData().targetPad = it
                            }))
                        }
                        this += listOf(
                            "取消" to {}
                        )
                    }
                    launchPadMenu(pad)
                }
            } else {
                "[lightgray]队伍发射台数量不足！" to { launchPadMenu(pad) }
            })
        playerInputing[uuid()] = false
        this += listOf(
            "[cyan]命名发射台！" to {
                val playerLastText = playerLastSendText[uuid()]
                sendMessage("------------\n[yellow]请输入此发射台改名名称(最大五个字)\n[white]------------")
                val startInputTime = Time.millis()
                var fail = true
                var newName = "- "
                playerInputing[uuid()] = true
                while (Time.timeSinceMillis(startInputTime) / 1000 <= 15) {
                    if (playerInputing[uuid()] == false) break
                    if (playerLastText != playerLastSendText[uuid()] && playerLastSendText[uuid()] != null){
                        if (playerLastSendText[uuid()] == pad.padData().name || playerLastSendText[uuid()]!!.length > 5){
                            break
                        }
                        newName += playerLastSendText[uuid()]
                        pad.padData().name = newName
                        fail = false
                        break
                    }
                    yield()
                }
                if (fail) {
                    sendMessage("命名失败！")
                } else {
                    sendMessage("命名成功！现在此发射台名为：${playerLastSendText[uuid()]}")
                }
                playerLastSendText[uuid()] = ""
            }
        )
        if (targetPad != null && targetPad.isValid){
            this += listOf("[cyan]传送你附近的军团单位至目标发射台！\n[red]可能会有副作用！" to {
                Call.effect(Fx.teleport, pad.x, pad.y, 0f, team().color)
                Units.nearby(team(), pad.x, pad.y, itemTransferRange) {
                    if (it.owner() == uuid() || it == unit())
                        launch(Dispatchers.game) {
                            var times = 0
                            while (true) {
                                Tmp.v1.rnd(Random.nextFloat() * itemTransferRange / 2)

                                var sx = targetPad.x + Tmp.v1.x
                                var sy = targetPad.y + Tmp.v1.y

                                if (it.canPass(World.toTile(sx), World.toTile(sy))) {
                                    while (!it.within(targetPad.x, targetPad.y, itemTransferRange)) {
                                        it.set(sx, sy)
                                        yield()
                                    }
                                    Call.effect(Fx.teleportOut, sx, sy, 0f, team().color)
                                    break
                                }
                                if (++times > 20) {
                                    sx = targetPad.x
                                    sy = targetPad.y
                                    while (!it.within(targetPad.x, targetPad.y, itemTransferRange)) {
                                        it.set(sx, sy)
                                        yield()
                                    }
                                    Call.effect(Fx.teleportOut, sx, sy, 0f, team().color)
                                    break
                                }
                            }
                            it.apply(StatusEffects.unmoving, (it.speed() / 1.5f) * 5f * 60f)
                            it.apply(StatusEffects.disarmed, (it.speed() / 1.5f) * 3.5f * 60f)
                            it.apply(StatusEffects.slow, (it.speed() / 1.5f) * 10.5f * 60f)
                            if (it == unit()) Call.setCameraPosition(con, targetPad.x, targetPad.y)
                        }
                }
                Call.effect(Fx.teleportActivate, targetPad.x, targetPad.y, 0f, team().color)
                targetPad.maxHealth /= 1.5f
                targetPad.clampHealth()
                pad.maxHealth /= 1.5f
                pad.clampHealth()
            })
        }
        this += listOf(
            "取消" to {}
        )
    }
}

listen<EventType.TapEvent> {
    val player = it.player
    if (player.dead()) return@listen
    if (it.tile.team() == player.team() &&
            it.tile.within(player.x,player.y, itemTransferRange)){
        launch(Dispatchers.game) {
            if (it.tile.block() is CoreBlock) player.cityMenu(it.tile.build as CoreBuild)
            if (it.tile.block() is LaunchPad) player.launchPadMenu(it.tile.build as LaunchPad.LaunchPadBuild)
        }
    }

}

listen<EventType.PlayerChatEvent>{
    playerLastSendText[it.player.uuid()] = it.message
}

listen<EventType.UnitDamageEvent>{ e ->
    val unit: mindustry.gen.Unit = e.unit
    val bullet: Bullet = e.bullet
    val team = bullet.team
    team.techData().bulletStatusEffect.forEach { (t, u) ->
        if (team.techData().checkEffectBulletHit()) {
            if (unit.hasEffect(StatusEffects.corroded))
                Units.nearby(unit.team, unit.x, unit.y, 8f * 8f){
                    if (unit.hasEffect(StatusEffects.corroded) && team.techData().checkEffectBulletHit()){
                        it.apply(StatusEffects.shocked, u)
                        it.apply(StatusEffects.blasted, u)
                    }
                    it.apply(t, unit.getDuration(t))
                }
            unit.apply(t, u)
        }
    }
}

listenPacket2Server<UnitControlCallPacket> {con, packet ->
    val unit: mindustry.gen.Unit = packet.unit
    val owner = unit.owner()
    if (owner == null){
        true
    } else {
        if (con.player.uuid() != owner) {
            Call.announce(con, "[red]你不是该单位的领主！无法控制")
            false
        } else {
            launch(Dispatchers.game) {
                while (!unit.isPlayer){
                    yield()
                }
                val effects = listOf(
                    StatusEffects.boss,
                    StatusEffects.shielded,
                    StatusEffects.overclock,
                    StatusEffects.overdrive,
                )
                val hadEffects = effects.toMutableList()
                while (unit.isPlayer) {
                    hadEffects.removeIf { !unit.hasEffect(it) }
                    effects.filter { it !in hadEffects }.forEach {
                        unit.apply(it, 1 * 60f)
                    }
                    yield()
                }
                effects.filter { it !in hadEffects }.forEach {
                    unit.unapply(it)
                }
            }
            true
        }
    }
}
