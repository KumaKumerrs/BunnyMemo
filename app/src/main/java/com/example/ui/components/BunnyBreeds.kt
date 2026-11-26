package com.example.ui.components

import androidx.compose.ui.graphics.Color
import com.example.data.CalendarDate
import com.example.ui.theme.BunnyEarBlush
import com.example.ui.theme.BunnyFurWhite
import com.example.ui.theme.BunnyOutlineInk

internal enum class BunnyEarStyle {
    UPRIGHT,
    LOP,
    SHORT_DWARF,
    TALL_BROAD,
    TASSELED_ANGORA,
    MANE_SHORT
}

internal enum class BunnyMarkingStyle {
    POINTED_EXTREMITIES,
    LOP_CHEEKS,
    DUTCH_SADDLE,
    LION_MANE,
    ANGORA_CLOUD,
    CREAM_BELLY,
    ENGLISH_SPOTTED,
    HARLEQUIN_SPLIT,
    DWARF_MARTEN,
    HOTOT_EYELINER
}

data class BunnyBreedPalette(
    val primaryCoat: Color,
    val secondaryPatch: Color,
    val bellyCoat: Color,
    val innerEar: Color,
    val blushCheek: Color,
    val eyeColor: Color,
    val outlineInk: Color,
    val noseColor: Color
)

enum class BunnyBreed(
    val month: Int,
    val monthName: String,
    val shortMonthName: String,
    val breedName: String,
    val traitTagline: String,
    val coatDescription: String,
    internal val earStyle: BunnyEarStyle,
    internal val markingStyle: BunnyMarkingStyle
) {
    HIMALAYAN(
        month = 1,
        monthName = "January",
        shortMonthName = "Jan",
        breedName = "Himalayan",
        traitTagline = "Snow-tipped frost",
        coatDescription = "Frost-white fur with dark cocoa-slate pointed ears, nose & paws",
        earStyle = BunnyEarStyle.UPRIGHT,
        markingStyle = BunnyMarkingStyle.POINTED_EXTREMITIES
    ),
    HOLLAND_LOP(
        month = 2,
        monthName = "February",
        shortMonthName = "Feb",
        breedName = "Holland Lop",
        traitTagline = "Velvet lop ears",
        coatDescription = "Warm buttercream-fawn coat with signature soft drooping lop ears",
        earStyle = BunnyEarStyle.LOP,
        markingStyle = BunnyMarkingStyle.LOP_CHEEKS
    ),
    DUTCH(
        month = 3,
        monthName = "March",
        shortMonthName = "Mar",
        breedName = "Dutch",
        traitTagline = "Saddle & white blaze",
        coatDescription = "Slate-blue saddle, cheeks & upright ears with a crisp white blaze",
        earStyle = BunnyEarStyle.UPRIGHT,
        markingStyle = BunnyMarkingStyle.DUTCH_SADDLE
    ),
    LIONHEAD(
        month = 4,
        monthName = "April",
        shortMonthName = "Apr",
        breedName = "Lionhead",
        traitTagline = "Fluffy golden mane",
        coatDescription = "Honey-cream fur framed by a scalloped fluffy lion mane & perky ears",
        earStyle = BunnyEarStyle.MANE_SHORT,
        markingStyle = BunnyMarkingStyle.LION_MANE
    ),
    ENGLISH_ANGORA(
        month = 5,
        monthName = "May",
        shortMonthName = "May",
        breedName = "English Angora",
        traitTagline = "Cloud-puff wool",
        coatDescription = "Scalloped pearl-white wool coat with tufted ear tips & cozy bangs",
        earStyle = BunnyEarStyle.TASSELED_ANGORA,
        markingStyle = BunnyMarkingStyle.ANGORA_CLOUD
    ),
    MINI_REX(
        month = 6,
        monthName = "June",
        shortMonthName = "Jun",
        breedName = "Mini Rex",
        traitTagline = "Plush castor velvet",
        coatDescription = "Velvety cinnamon-caramel coat with soft ivory chin & underbelly",
        earStyle = BunnyEarStyle.UPRIGHT,
        markingStyle = BunnyMarkingStyle.CREAM_BELLY
    ),
    ENGLISH_SPOT(
        month = 7,
        monthName = "July",
        shortMonthName = "Jul",
        breedName = "English Spot",
        traitTagline = "Dotted summer coat",
        coatDescription = "Bright ivory coat with navy-charcoal spots, eye rings & butterfly nose",
        earStyle = BunnyEarStyle.UPRIGHT,
        markingStyle = BunnyMarkingStyle.ENGLISH_SPOTTED
    ),
    HARLEQUIN(
        month = 8,
        monthName = "August",
        shortMonthName = "Aug",
        breedName = "Harlequin",
        traitTagline = "Two-tone calico",
        coatDescription = "Playful split apricot-amber & slate-charcoal alternating ears & cheeks",
        earStyle = BunnyEarStyle.UPRIGHT,
        markingStyle = BunnyMarkingStyle.HARLEQUIN_SPLIT
    ),
    CALIFORNIAN(
        month = 9,
        monthName = "September",
        shortMonthName = "Sep",
        breedName = "Californian",
        traitTagline = "Smoky autumn points",
        coatDescription = "Warm ivory-cream plump coat with smoky charcoal-sepia points",
        earStyle = BunnyEarStyle.UPRIGHT,
        markingStyle = BunnyMarkingStyle.POINTED_EXTREMITIES
    ),
    FLEMISH_GIANT(
        month = 10,
        monthName = "October",
        shortMonthName = "Oct",
        breedName = "Flemish Giant",
        traitTagline = "Noble tall ears",
        coatDescription = "Sandy-chestnut gentle giant with extra-tall V-ears & cream chest",
        earStyle = BunnyEarStyle.TALL_BROAD,
        markingStyle = BunnyMarkingStyle.CREAM_BELLY
    ),
    NETHERLAND_DWARF(
        month = 11,
        monthName = "November",
        shortMonthName = "Nov",
        breedName = "Netherland Dwarf",
        traitTagline = "Pocket chinchilla",
        coatDescription = "Compact silver-ash dwarf coat, tiny rounded ears & starry eyes",
        earStyle = BunnyEarStyle.SHORT_DWARF,
        markingStyle = BunnyMarkingStyle.DWARF_MARTEN
    ),
    BLANC_DE_HOTOT(
        month = 12,
        monthName = "December",
        shortMonthName = "Dec",
        breedName = "Blanc de Hotot",
        traitTagline = "Winter eyeliner",
        coatDescription = "Pure winter-frost coat with bold dark Hotot eyeliner rings",
        earStyle = BunnyEarStyle.UPRIGHT,
        markingStyle = BunnyMarkingStyle.HOTOT_EYELINER
    );

    internal fun palette(isDarkTheme: Boolean): BunnyBreedPalette {
        val ink = if (isDarkTheme) Color(0xFF17273C) else BunnyOutlineInk
        return when (this) {
            HIMALAYAN -> BunnyBreedPalette(
                primaryCoat = BunnyFurWhite,
                secondaryPatch = Color(0xFF423D4E),
                bellyCoat = BunnyFurWhite,
                innerEar = Color(0xFFE7B6C4),
                blushCheek = Color(0xFFF3B8C8),
                eyeColor = Color(0xFF8C3654),
                outlineInk = ink,
                noseColor = Color(0xFF423D4E)
            )
            HOLLAND_LOP -> BunnyBreedPalette(
                primaryCoat = Color(0xFFF6DFC1),
                secondaryPatch = Color(0xFFD9AF84),
                bellyCoat = Color(0xFFFFF5E8),
                innerEar = Color(0xFFF3B8BE),
                blushCheek = Color(0xFFF4B3B9),
                eyeColor = ink,
                outlineInk = ink,
                noseColor = Color(0xFFD0868C)
            )
            DUTCH -> BunnyBreedPalette(
                primaryCoat = BunnyFurWhite,
                secondaryPatch = Color(0xFF4C6F9C),
                bellyCoat = BunnyFurWhite,
                innerEar = Color(0xFFBBD7F8),
                blushCheek = BunnyEarBlush,
                eyeColor = ink,
                outlineInk = ink,
                noseColor = Color(0xFFD98B96)
            )
            LIONHEAD -> BunnyBreedPalette(
                primaryCoat = Color(0xFFFFF3DA),
                secondaryPatch = Color(0xFFE9BA6E),
                bellyCoat = Color(0xFFFFFAEE),
                innerEar = Color(0xFFF7D4BC),
                blushCheek = Color(0xFFF4B7AD),
                eyeColor = ink,
                outlineInk = ink,
                noseColor = Color(0xFFCE8A7B)
            )
            ENGLISH_ANGORA -> BunnyBreedPalette(
                primaryCoat = Color(0xFFFAFBFD),
                secondaryPatch = Color(0xFFDDEBF9),
                bellyCoat = Color(0xFFFFFFFF),
                innerEar = Color(0xFFF5CAD6),
                blushCheek = Color(0xFFF7C2CF),
                eyeColor = Color(0xFF314D70),
                outlineInk = ink,
                noseColor = Color(0xFFE196A7)
            )
            MINI_REX -> BunnyBreedPalette(
                primaryCoat = Color(0xFFD89D72),
                secondaryPatch = Color(0xFFB5784E),
                bellyCoat = Color(0xFFFDF0E2),
                innerEar = Color(0xFFEEC5AE),
                blushCheek = Color(0xFFE9A08E),
                eyeColor = ink,
                outlineInk = ink,
                noseColor = Color(0xFF9F6344)
            )
            ENGLISH_SPOT -> BunnyBreedPalette(
                primaryCoat = Color(0xFFFCFDFE),
                secondaryPatch = Color(0xFF334B6B),
                bellyCoat = BunnyFurWhite,
                innerEar = Color(0xFFD3E4F8),
                blushCheek = BunnyEarBlush,
                eyeColor = ink,
                outlineInk = ink,
                noseColor = Color(0xFF334B6B)
            )
            HARLEQUIN -> BunnyBreedPalette(
                primaryCoat = Color(0xFFF2AA68),
                secondaryPatch = Color(0xFF3C4D63),
                bellyCoat = Color(0xFFFFEFDB),
                innerEar = Color(0xFFF3C6A8),
                blushCheek = Color(0xFFF09F8C),
                eyeColor = ink,
                outlineInk = ink,
                noseColor = Color(0xFF3C4D63)
            )
            CALIFORNIAN -> BunnyBreedPalette(
                primaryCoat = Color(0xFFFBF7EE),
                secondaryPatch = Color(0xFF544B49),
                bellyCoat = Color(0xFFFFFCF5),
                innerEar = Color(0xFFD6C8C4),
                blushCheek = Color(0xFFEFC1C1),
                eyeColor = Color(0xFF6B3945),
                outlineInk = ink,
                noseColor = Color(0xFF544B49)
            )
            FLEMISH_GIANT -> BunnyBreedPalette(
                primaryCoat = Color(0xFFDAB794),
                secondaryPatch = Color(0xFFB48C68),
                bellyCoat = Color(0xFFF9EAD8),
                innerEar = Color(0xFFE9C4AF),
                blushCheek = Color(0xFFE9AC9B),
                eyeColor = ink,
                outlineInk = ink,
                noseColor = Color(0xFF8C6549)
            )
            NETHERLAND_DWARF -> BunnyBreedPalette(
                primaryCoat = Color(0xFFC8D3E1),
                secondaryPatch = Color(0xFF8E9EB3),
                bellyCoat = Color(0xFFF1F6FC),
                innerEar = Color(0xFFF1C6D3),
                blushCheek = Color(0xFFF2BAC9),
                eyeColor = Color(0xFF20344E),
                outlineInk = ink,
                noseColor = Color(0xFFD68D9E)
            )
            BLANC_DE_HOTOT -> BunnyBreedPalette(
                primaryCoat = Color(0xFFFCFEFF),
                secondaryPatch = Color(0xFF253447),
                bellyCoat = BunnyFurWhite,
                innerEar = Color(0xFFCDE5FD),
                blushCheek = Color(0xFFD0E6FB),
                eyeColor = Color(0xFF1F2D3E),
                outlineInk = ink,
                noseColor = Color(0xFFDF97A8)
            )
        }
    }

    companion object {
        fun forMonth(month: Int): BunnyBreed {
            val normalized = ((month - 1).mod(12)) + 1
            return entries.firstOrNull { it.month == normalized } ?: NETHERLAND_DWARF
        }

        fun forCurrentMonth(): BunnyBreed = forMonth(CalendarDate.today().month)
    }
}
