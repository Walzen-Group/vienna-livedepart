package com.walzengroup.viennadepart.tile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.walzengroup.viennadepart.MainActivity
import com.walzengroup.viennadepart.R
import com.walzengroup.viennadepart.data.Favorite
import com.walzengroup.viennadepart.data.FavoritesStore
import com.walzengroup.viennadepart.ui.theme.ModeColor

/** Intent extra: which favorite line the app should open to (locate → nearest stop → departures). */
const val EXTRA_FAVORITE_LINE = "favorite_line"

private const val RESOURCES_VERSION = "1"
private const val TRAIN_ID = "train"
private const val CELL_DP = 48f
private const val V_GAP_DP = 14f // within a column — sets the vertical hex spacing
private const val H_GAP_DP = 7f  // between columns
private val LINE_BG = 0xFF1C1C22.toInt()
private val APP_BG = 0xFF202028.toInt()
private val LINE_BORDER = 0x14FFFFFF // rgba(255,255,255,.08)
private val APP_BORDER = 0x24FFFFFF  // rgba(255,255,255,.14)

/**
 * A honeycomb Tile of the pinned lines (up to [FavoritesStore.MAX] = 6): six circular line
 * buttons in a hexagon around a centre app button (a train). Tapping a line launches the app
 * into that favorite's located departures; the centre opens the app. Static, no scroll.
 */
class FavoritesTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val root = honeycomb(FavoritesStore.list(this))
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val res = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                TRAIN_ID,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.ic_train)
                            .build(),
                    )
                    .build(),
            )
            .build()
        return ResolvableFuture.create<ResourceBuilders.Resources>().apply { set(res) }
    }

    // A hexagon around the centre, built as three columns: left [upper, lower], centre
    // [top, app, bottom], right [upper, lower]. The Row centres the two-cell side columns
    // against the three-cell centre, so their cells land in the hex valleys. Slots are filled
    // clockwise from the top; empty ones stay as invisible cells so the shape holds.
    private fun honeycomb(favorites: List<Favorite>): LayoutElement {
        fun cell(i: Int): LayoutElement {
            val f = favorites.getOrNull(i) ?: return emptyCell()
            val textArgb = lerp(ModeColor.forLine(f.line, f.type), Color.White, 0.18f).toArgb()
            return lineButton(f.line, textArgb)
        }
        val vGap = Spacer.Builder().setHeight(dp(V_GAP_DP)).build()
        val hGap = Spacer.Builder().setWidth(dp(H_GAP_DP)).build()

        val colLeft = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(cell(5)).addContent(vGap).addContent(cell(4))
            .build()
        val colCenter = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(cell(0)).addContent(vGap).addContent(centerButton()).addContent(vGap).addContent(cell(3))
            .build()
        val colRight = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(cell(1)).addContent(vGap).addContent(cell(2))
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(colLeft).addContent(hGap).addContent(colCenter).addContent(hGap).addContent(colRight)
                    .build(),
            )
            .build()
    }

    private fun lineButton(line: String, textArgb: Int): LayoutElement {
        val label = Text.Builder()
            .setText(line)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(16f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .setColor(argb(textArgb))
                    .build(),
            )
            .build()
        return circle(label, LINE_BG, LINE_BORDER, "line-$line", launch(line))
    }

    private fun centerButton(): LayoutElement {
        val train = Image.Builder()
            .setResourceId(TRAIN_ID)
            .setWidth(dp(24f))
            .setHeight(dp(24f))
            .build()
        return circle(train, APP_BG, APP_BORDER, "center", launch(null))
    }

    private fun emptyCell(): LayoutElement =
        Box.Builder().setWidth(dp(CELL_DP)).setHeight(dp(CELL_DP)).build()

    private fun circle(
        content: LayoutElement,
        bgArgb: Int,
        borderArgb: Int,
        clickId: String,
        action: ActionBuilders.Action,
    ): LayoutElement =
        Box.Builder()
            .setWidth(dp(CELL_DP))
            .setHeight(dp(CELL_DP))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(bgArgb))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(CELL_DP / 2f)).build())
                            .build(),
                    )
                    .setBorder(
                        ModifiersBuilders.Border.Builder()
                            .setWidth(dp(1f))
                            .setColor(argb(borderArgb))
                            .build(),
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder().setId(clickId).setOnClick(action).build(),
                    )
                    .build(),
            )
            .addContent(content)
            .build()

    // Launch MainActivity; with a line, pass it as an extra so the app opens that favorite.
    private fun launch(line: String?): ActionBuilders.LaunchAction {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
        if (line != null) {
            activity.addKeyToExtraMapping(
                EXTRA_FAVORITE_LINE,
                ActionBuilders.AndroidStringExtra.Builder().setValue(line).build(),
            )
        }
        return ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()
    }
}
