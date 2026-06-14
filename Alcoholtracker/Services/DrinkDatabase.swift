import Foundation
import SwiftData

// MARK: - DrinkDatabase
// Static catalogue seeded once and updated on version bump.
// Bump catalogVersion whenever new default templates are added.
// The seeding logic is name-based: existing templates are never overwritten.

enum DrinkDatabase {

    private static let catalogVersion = 5
    private static let versionKey     = "DrinkDatabaseVersion"

    static let defaults: [DrinkTemplate] = bier + wein + sektUndSchaumwein
        + spirituosen + likoere + shots + cocktails + mischgetraenke
        + cider + likoerweine + neueDrinks + csvSortiment

    // MARK: Bier

    private static let bier: [DrinkTemplate] = [

        // Deutsche Pils
        DrinkTemplate(name: "Augustiner Helle",             category: .beer, volume: 500, abv: 5.2,  calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Augustiner Edelstoff",         category: .beer, volume: 500, abv: 5.6,  calories: 222, iconName: "mug.fill"),
        DrinkTemplate(name: "Becks Pils",                   category: .beer, volume: 330, abv: 4.9,  calories: 146, iconName: "mug.fill"),
        DrinkTemplate(name: "Berliner Kindl Pils",          category: .beer, volume: 500, abv: 5.1,  calories: 205, iconName: "mug.fill"),
        DrinkTemplate(name: "Bitburger Pils",               category: .beer, volume: 330, abv: 4.8,  calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Corona Extra",                 category: .beer, volume: 355, abv: 4.5,  calories: 148, iconName: "mug.fill"),
        DrinkTemplate(name: "Erdinger Alkoholfrei",         category: .beer, volume: 500, abv: 0.4,  calories: 125, iconName: "mug.fill"),
        DrinkTemplate(name: "Erdinger Weißbier",            category: .beer, volume: 500, abv: 5.3,  calories: 220, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Franziskaner Weissbier",       category: .beer, volume: 500, abv: 5.0,  calories: 215, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Heineken",                     category: .beer, volume: 330, abv: 5.0,  calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Hofbräu Original",             category: .beer, volume: 500, abv: 5.1,  calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Jever Pilsener",               category: .beer, volume: 330, abv: 4.9,  calories: 145, iconName: "mug.fill"),
        DrinkTemplate(name: "Krombacher Radler",            category: .beer, volume: 500, abv: 2.5,  calories: 205, iconName: "mug.fill"),
        DrinkTemplate(name: "Löwenbräu Original",           category: .beer, volume: 500, abv: 5.2,  calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Paulaner Hefe-Weißbier",       category: .beer, volume: 500, abv: 5.5,  calories: 225, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Paulaner Spezi",               category: .mixed, volume: 500, abv: 0.0, calories: 200, iconName: "mug.fill"),
        DrinkTemplate(name: "Smirnoff Ice",                 category: .mixed, volume: 275, abv: 4.0, calories: 195, iconName: "mug.fill"),
        DrinkTemplate(name: "Tannenzäpfle Rothaus",         category: .beer, volume: 330, abv: 5.1,  calories: 145, iconName: "mug.fill"),
        DrinkTemplate(name: "Three Sixty Vodka Cola",       category: .mixed, volume: 330, abv: 10.0, calories: 215, iconName: "mug.fill"),
        DrinkTemplate(name: "Warsteiner Premium",           category: .beer, volume: 330, abv: 4.8,  calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Weihenstephaner Hefeweißbier", category: .beer, volume: 500, abv: 5.4,  calories: 220, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Bacardi Cola (Dose)",          category: .mixed, volume: 330, abv: 10.0, calories: 230, iconName: "mug.fill"),
        DrinkTemplate(name: "Captain Morgan Cola",          category: .mixed, volume: 330, abv: 10.0, calories: 220, iconName: "mug.fill"),

        // Neue Pils
        DrinkTemplate(name: "Flensburger Pilsener",         category: .beer, volume: 330, abv: 4.8,  calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Stauder Premium Pils",         category: .beer, volume: 330, abv: 4.6,  calories: 137, iconName: "mug.fill"),
        DrinkTemplate(name: "Astra Urtyp",                  category: .beer, volume: 330, abv: 4.9,  calories: 145, iconName: "mug.fill"),

        // Helles und Lager
        DrinkTemplate(name: "Tegernseer Hell",              category: .beer, volume: 500, abv: 4.8,  calories: 200, iconName: "mug.fill"),
        DrinkTemplate(name: "Spaten Münchner Hell",         category: .beer, volume: 500, abv: 5.2,  calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Hacker-Pschorr Münchner Gold", category: .beer, volume: 500, abv: 5.5,  calories: 220, iconName: "mug.fill"),
        DrinkTemplate(name: "Andechser Bergbock Hell",      category: .beer, volume: 330, abv: 6.9,  calories: 178, iconName: "mug.fill"),

        // Weißbier (Weizenglas is tall and narrow → wineglass.fill best match)
        DrinkTemplate(name: "Schneider Weisse Original",    category: .beer, volume: 500, abv: 5.4,  calories: 220, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Maisel's Weisse",              category: .beer, volume: 500, abv: 5.2,  calories: 210, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Erdinger Kristall",            category: .beer, volume: 500, abv: 5.3,  calories: 215, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Erdinger Dunkel",              category: .beer, volume: 500, abv: 5.6,  calories: 225, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Schöfferhofer Hefeweizen",     category: .beer, volume: 500, abv: 5.0,  calories: 210, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Hacker-Pschorr Hefe Weiße",   category: .beer, volume: 500, abv: 5.5,  calories: 225, iconName: "wineglass.fill"),

        // International
        DrinkTemplate(name: "Stella Artois",                category: .beer, volume: 330, abv: 5.0,  calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Amstel",                       category: .beer, volume: 330, abv: 5.0,  calories: 145, iconName: "mug.fill"),
        DrinkTemplate(name: "Carlsberg",                    category: .beer, volume: 330, abv: 5.0,  calories: 143, iconName: "mug.fill"),
        DrinkTemplate(name: "Tuborg",                       category: .beer, volume: 330, abv: 4.6,  calories: 137, iconName: "mug.fill"),
        DrinkTemplate(name: "Budweiser",                    category: .beer, volume: 330, abv: 5.0,  calories: 147, iconName: "mug.fill"),
        DrinkTemplate(name: "Coors Light",                  category: .beer, volume: 330, abv: 4.2,  calories: 102, iconName: "mug.fill"),
        DrinkTemplate(name: "Miller High Life",             category: .beer, volume: 330, abv: 4.6,  calories: 140, iconName: "mug.fill"),
        DrinkTemplate(name: "Asahi Super Dry",              category: .beer, volume: 330, abv: 5.0,  calories: 140, iconName: "mug.fill"),
        DrinkTemplate(name: "Sapporo Premium",              category: .beer, volume: 330, abv: 4.9,  calories: 138, iconName: "mug.fill"),
        DrinkTemplate(name: "Singha",                       category: .beer, volume: 330, abv: 5.0,  calories: 147, iconName: "mug.fill"),
        DrinkTemplate(name: "Tiger Beer",                   category: .beer, volume: 330, abv: 5.0,  calories: 145, iconName: "mug.fill"),
        DrinkTemplate(name: "Tsingtao",                     category: .beer, volume: 330, abv: 4.7,  calories: 134, iconName: "mug.fill"),
        DrinkTemplate(name: "Modelo Especial",              category: .beer, volume: 355, abv: 4.4,  calories: 143, iconName: "mug.fill"),
        DrinkTemplate(name: "Pilsner Urquell",              category: .beer, volume: 330, abv: 4.4,  calories: 132, iconName: "mug.fill"),
        DrinkTemplate(name: "Kozel Premium",                category: .beer, volume: 330, abv: 4.6,  calories: 138, iconName: "mug.fill"),
        DrinkTemplate(name: "Staropramen",                  category: .beer, volume: 330, abv: 5.0,  calories: 145, iconName: "mug.fill"),

        // Spezialitäten und Dunkel
        DrinkTemplate(name: "Köstritzer Schwarzbier",       category: .beer, volume: 330, abv: 4.8,  calories: 145, iconName: "mug.fill"),
        DrinkTemplate(name: "Guinness Draught",             category: .beer, volume: 500, abv: 4.2,  calories: 190, iconName: "mug.fill"),
        DrinkTemplate(name: "Murphy's Irish Stout",         category: .beer, volume: 500, abv: 4.0,  calories: 185, iconName: "mug.fill"),
        DrinkTemplate(name: "Erdinger Pikantus",            category: .beer, volume: 500, abv: 7.3,  calories: 270, iconName: "mug.fill"),
        DrinkTemplate(name: "Andechser Doppelbock Dunkel",  category: .beer, volume: 500, abv: 7.1,  calories: 265, iconName: "mug.fill"),
        DrinkTemplate(name: "Aventinus Weizen-Eisbock",     category: .beer, volume: 330, abv: 12.0, calories: 255, iconName: "mug.fill"),
        DrinkTemplate(name: "Salvator Doppelbock",          category: .beer, volume: 500, abv: 7.9,  calories: 285, iconName: "mug.fill"),
        DrinkTemplate(name: "Maisel's Weisse Doppelbock",   category: .beer, volume: 330, abv: 8.0,  calories: 180, iconName: "mug.fill"),

        // Radler
        DrinkTemplate(name: "Paulaner Radler",              category: .beer, volume: 500, abv: 2.5,  calories: 205, iconName: "mug.fill"),
        DrinkTemplate(name: "Erdinger Radler",              category: .beer, volume: 500, abv: 2.5,  calories: 200, iconName: "mug.fill"),
        DrinkTemplate(name: "Schöfferhofer Pomegranate",    category: .beer, volume: 500, abv: 2.5,  calories: 215, iconName: "mug.fill"),
        DrinkTemplate(name: "Berliner Weisse mit Schuss",   category: .beer, volume: 300, abv: 2.8,  calories: 155, iconName: "mug.fill"),
        DrinkTemplate(name: "Astra Kiezmische",             category: .beer, volume: 500, abv: 2.5,  calories: 205, iconName: "mug.fill"),

        // Kölsch (served in the narrow 200ml Stange glass → cylinder.fill)
        DrinkTemplate(name: "Reissdorf Kölsch",             category: .beer, volume: 200, abv: 4.8,  calories: 87,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Gaffel Kölsch",                category: .beer, volume: 200, abv: 4.8,  calories: 87,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Mühlen Kölsch",                category: .beer, volume: 200, abv: 4.8,  calories: 87,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Päffgen Kölsch",               category: .beer, volume: 200, abv: 4.9,  calories: 89,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Sünner Kölsch",                category: .beer, volume: 200, abv: 4.8,  calories: 87,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Sion Kölsch",                  category: .beer, volume: 200, abv: 4.8,  calories: 87,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Dom Kölsch",                   category: .beer, volume: 200, abv: 4.8,  calories: 87,  iconName: "cylinder.fill"),

        // Alkoholfrei
        DrinkTemplate(name: "Beck's Blue",                  category: .beer, volume: 330, abv: 0.0,  calories: 38,  iconName: "mug.fill"),
        DrinkTemplate(name: "Paulaner Weißbier Alkoholfrei",category: .beer, volume: 500, abv: 0.5,  calories: 125, iconName: "mug.fill"),
        DrinkTemplate(name: "Clausthaler Original",         category: .beer, volume: 330, abv: 0.4,  calories: 85,  iconName: "mug.fill"),
        DrinkTemplate(name: "Bitburger 0.0%",               category: .beer, volume: 330, abv: 0.0,  calories: 38,  iconName: "mug.fill"),
        DrinkTemplate(name: "Krombacher 0.0%",              category: .beer, volume: 330, abv: 0.0,  calories: 35,  iconName: "mug.fill"),
    ]

    // MARK: Wein

    private static let wein: [DrinkTemplate] = [
        DrinkTemplate(name: "Rotwein Glas",                 category: .wine, volume: 200, abv: 13.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Weißwein Glas",                category: .wine, volume: 200, abv: 12.0, calories: 160, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Roséwein Glas",                category: .wine, volume: 200, abv: 11.5, calories: 165, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Riesling Glas",                category: .wine, volume: 200, abv: 11.5, calories: 155, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Glühwein",                     category: .wine, volume: 200, abv: 9.0,  calories: 250, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Federweißer",                  category: .wine, volume: 200, abv: 6.0,  calories: 140, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lambrusco Glas",               category: .wine, volume: 200, abv: 11.0, calories: 160, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Prosecco Glas",                category: .wine, volume: 100, abv: 11.0, calories: 75,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sekt Glas",                    category: .wine, volume: 100, abv: 11.5, calories: 80,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Champagner Glas",              category: .wine, volume: 100, abv: 12.0, calories: 90,  iconName: "wineglass.fill"),

        // Weitere Rebsorten und Typen
        DrinkTemplate(name: "Grauburgunder",                category: .wine, volume: 200, abv: 12.5, calories: 165, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Spätburgunder",                category: .wine, volume: 200, abv: 13.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sauvignon Blanc",              category: .wine, volume: 200, abv: 12.5, calories: 165, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Chardonnay",                   category: .wine, volume: 200, abv: 13.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Cabernet Sauvignon",           category: .wine, volume: 200, abv: 13.5, calories: 175, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Merlot",                       category: .wine, volume: 200, abv: 13.5, calories: 175, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Pinot Noir",                   category: .wine, volume: 200, abv: 13.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Tempranillo",                  category: .wine, volume: 200, abv: 13.5, calories: 175, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sangiovese",                   category: .wine, volume: 200, abv: 13.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sangria",                      category: .wine, volume: 200, abv: 9.0,  calories: 200, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Rotwein lieblich",             category: .wine, volume: 200, abv: 11.5, calories: 175, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Weißwein halbtrocken",         category: .wine, volume: 200, abv: 11.5, calories: 165, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Weißwein lieblich",            category: .wine, volume: 200, abv: 11.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Roséwein halbtrocken",         category: .wine, volume: 200, abv: 11.0, calories: 165, iconName: "wineglass.fill"),
    ]

    // MARK: Sekt und Schaumwein

    private static let sektUndSchaumwein: [DrinkTemplate] = [
        DrinkTemplate(name: "Sekt trocken",                 category: .sparkling, volume: 100, abv: 11.5, calories: 80,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sekt halbtrocken",             category: .sparkling, volume: 100, abv: 11.5, calories: 85,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Prosecco",                     category: .sparkling, volume: 100, abv: 11.0, calories: 77,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Champagner",                   category: .sparkling, volume: 100, abv: 12.0, calories: 84,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Crémant",                      category: .sparkling, volume: 100, abv: 12.0, calories: 84,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Cava",                         category: .sparkling, volume: 100, abv: 11.5, calories: 80,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Asti Spumante",                category: .sparkling, volume: 100, abv: 7.5,  calories: 60,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lambrusco frizzante",          category: .sparkling, volume: 100, abv: 8.0,  calories: 65,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Henkell Trocken",              category: .sparkling, volume: 100, abv: 11.5, calories: 80,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Mumm Extra Dry",               category: .sparkling, volume: 100, abv: 11.5, calories: 80,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Rotkäppchen Sekt",             category: .sparkling, volume: 100, abv: 11.5, calories: 80,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Söhnlein Brillant",            category: .sparkling, volume: 100, abv: 11.0, calories: 77,  iconName: "wineglass.fill"),
    ]

    // MARK: Spirituosen

    private static let spirituosen: [DrinkTemplate] = [
        DrinkTemplate(name: "Aperol Pur",                   category: .spirits, volume: 40,  abv: 11.0, calories: 60,  iconName: "drop.fill"),
        DrinkTemplate(name: "Baileys",                      category: .spirits, volume: 40,  abv: 17.0, calories: 130, iconName: "drop.fill"),
        DrinkTemplate(name: "Campari Pur",                  category: .spirits, volume: 40,  abv: 25.0, calories: 60,  iconName: "drop.fill"),
        DrinkTemplate(name: "Fernet Branca",                category: .spirits, volume: 30,  abv: 39.0, calories: 75,  iconName: "drop.fill"),
        DrinkTemplate(name: "Gin Pur",                      category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Jägermeister",                 category: .spirits, volume: 20,  abv: 35.0, calories: 50,  iconName: "drop.fill"),
        DrinkTemplate(name: "Limoncello",                   category: .spirits, volume: 30,  abv: 30.0, calories: 80,  iconName: "drop.fill"),
        DrinkTemplate(name: "Ouzo",                         category: .spirits, volume: 20,  abv: 38.0, calories: 55,  iconName: "drop.fill"),
        DrinkTemplate(name: "Pfeffi",                       category: .spirits, volume: 20,  abv: 30.0, calories: 55,  iconName: "drop.fill"),
        DrinkTemplate(name: "Rum Shot",                     category: .spirits, volume: 20,  abv: 40.0, calories: 55,  iconName: "drop.fill"),
        DrinkTemplate(name: "Rum (neat)",                   category: .spirits, volume: 40,  abv: 40.0, calories: 90,  iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Schnaps Korn",                 category: .spirits, volume: 20,  abv: 32.0, calories: 45,  iconName: "drop.fill"),
        DrinkTemplate(name: "Tequila Shot",                 category: .spirits, volume: 20,  abv: 38.0, calories: 50,  iconName: "drop.fill"),
        DrinkTemplate(name: "Vodka Shot",                   category: .spirits, volume: 20,  abv: 40.0, calories: 55,  iconName: "drop.fill"),
        DrinkTemplate(name: "Whiskey Shot",                 category: .spirits, volume: 20,  abv: 40.0, calories: 55,  iconName: "drop.fill"),
        DrinkTemplate(name: "Whiskey (neat)",               category: .spirits, volume: 40,  abv: 43.0, calories: 95,  iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Williams Birne",               category: .spirits, volume: 20,  abv: 40.0, calories: 55,  iconName: "drop.fill"),

        // Vodka Marken
        DrinkTemplate(name: "Smirnoff Red Label",           category: .spirits, volume: 40,  abv: 37.5, calories: 90,  iconName: "drop.fill"),
        DrinkTemplate(name: "Absolut Vodka",                category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Russian Standard Vodka",       category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Stolichnaya Vodka",            category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),

        // Gin Marken
        DrinkTemplate(name: "Bombay Sapphire",              category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Tanqueray Gin",                category: .spirits, volume: 40,  abv: 43.1, calories: 100, iconName: "drop.fill"),
        DrinkTemplate(name: "Monkey 47 Gin",                category: .spirits, volume: 40,  abv: 47.0, calories: 110, iconName: "drop.fill"),
        DrinkTemplate(name: "The Botanist Gin",             category: .spirits, volume: 40,  abv: 46.0, calories: 108, iconName: "drop.fill"),
        DrinkTemplate(name: "Beefeater Gin",                category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Gordon's Gin",                 category: .spirits, volume: 40,  abv: 37.5, calories: 90,  iconName: "drop.fill"),

        // Rum Marken
        DrinkTemplate(name: "Captain Morgan Spiced",        category: .spirits, volume: 40,  abv: 35.0, calories: 85,  iconName: "drop.fill"),
        DrinkTemplate(name: "Havana Club 3 Anos",           category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Diplomatico Reserva",          category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Ron Zacapa 23",                category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Kraken Spiced Rum",            category: .spirits, volume: 40,  abv: 47.0, calories: 110, iconName: "drop.fill"),
        DrinkTemplate(name: "Stroh Rum 80",                 category: .spirits, volume: 20,  abv: 80.0, calories: 90,  iconName: "drop.fill"),

        // Whisky und Whiskey Marken
        DrinkTemplate(name: "Jack Daniel's",                category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Jim Beam",                     category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Jameson",                      category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Johnnie Walker Red",           category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Johnnie Walker Black",         category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Glenfiddich 12",               category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "The Macallan 12",              category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Bulleit Bourbon",              category: .spirits, volume: 40,  abv: 45.0, calories: 103, iconName: "drop.fill"),
        DrinkTemplate(name: "Maker's Mark",                 category: .spirits, volume: 40,  abv: 45.0, calories: 103, iconName: "drop.fill"),

        // Tequila Marken
        DrinkTemplate(name: "Jose Cuervo Gold",             category: .spirits, volume: 40,  abv: 38.0, calories: 90,  iconName: "drop.fill"),
        DrinkTemplate(name: "Sierra Silver Tequila",        category: .spirits, volume: 40,  abv: 38.0, calories: 90,  iconName: "drop.fill"),
        DrinkTemplate(name: "Patron Silver",                category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),

        // Brände und Schnäpse
        DrinkTemplate(name: "Aquavit",                      category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Doppelkorn",                   category: .spirits, volume: 20,  abv: 38.0, calories: 45,  iconName: "drop.fill"),
        DrinkTemplate(name: "Korn",                         category: .spirits, volume: 20,  abv: 32.0, calories: 38,  iconName: "drop.fill"),
        DrinkTemplate(name: "Mirabelle",                    category: .spirits, volume: 20,  abv: 40.0, calories: 45,  iconName: "drop.fill"),
        DrinkTemplate(name: "Zwetschge",                    category: .spirits, volume: 20,  abv: 40.0, calories: 45,  iconName: "drop.fill"),
        DrinkTemplate(name: "Kirsch",                       category: .spirits, volume: 20,  abv: 40.0, calories: 45,  iconName: "drop.fill"),

        // Vodka Erweiterung
        DrinkTemplate(name: "Beluga Noble",                 category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),

        // Gin Erweiterung
        DrinkTemplate(name: "Roku Gin",                     category: .spirits, volume: 40,  abv: 43.0, calories: 100, iconName: "drop.fill"),

        // Rum Erweiterung
        DrinkTemplate(name: "Old Pascas Brown 73",          category: .spirits, volume: 20,  abv: 73.0, calories: 105, iconName: "drop.fill"),

        // Whisky Erweiterung
        DrinkTemplate(name: "Chivas Regal 12",              category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Tullamore Dew",                category: .spirits, volume: 40,  abv: 40.0, calories: 95,  iconName: "drop.fill"),

        // Tequila Erweiterung
        DrinkTemplate(name: "Don Julio Blanco",             category: .spirits, volume: 40,  abv: 38.0, calories: 90,  iconName: "drop.fill"),
        DrinkTemplate(name: "Olmeca Altos Plata",           category: .spirits, volume: 40,  abv: 38.0, calories: 90,  iconName: "drop.fill"),
    ]

    // MARK: Liköre

    private static let likoere: [DrinkTemplate] = [
        DrinkTemplate(name: "Averna",                       category: .liqueur, volume: 40,  abv: 29.0, calories: 95,  iconName: "drop.fill"),
        DrinkTemplate(name: "Ramazzotti",                   category: .liqueur, volume: 40,  abv: 30.0, calories: 100, iconName: "drop.fill"),
        DrinkTemplate(name: "Cynar",                        category: .liqueur, volume: 40,  abv: 16.5, calories: 65,  iconName: "drop.fill"),
        DrinkTemplate(name: "Underberg",                    category: .liqueur, volume: 20,  abv: 44.0, calories: 62,  iconName: "drop.fill"),
        DrinkTemplate(name: "Pernod",                       category: .liqueur, volume: 40,  abv: 40.0, calories: 90,  iconName: "drop.fill"),
        DrinkTemplate(name: "Pastis",                       category: .liqueur, volume: 40,  abv: 40.0, calories: 90,  iconName: "drop.fill"),
        DrinkTemplate(name: "Cointreau",                    category: .liqueur, volume: 40,  abv: 40.0, calories: 140, iconName: "drop.fill"),
        DrinkTemplate(name: "Grand Marnier",                category: .liqueur, volume: 40,  abv: 40.0, calories: 135, iconName: "drop.fill"),
        DrinkTemplate(name: "Triple Sec",                   category: .liqueur, volume: 40,  abv: 30.0, calories: 120, iconName: "drop.fill"),
        DrinkTemplate(name: "Malibu Kokos",                 category: .liqueur, volume: 40,  abv: 21.0, calories: 145, iconName: "drop.fill"),
        DrinkTemplate(name: "Kahlúa",                       category: .liqueur, volume: 40,  abv: 20.0, calories: 120, iconName: "drop.fill"),
        DrinkTemplate(name: "Tia Maria",                    category: .liqueur, volume: 40,  abv: 20.0, calories: 120, iconName: "drop.fill"),
        DrinkTemplate(name: "Galliano",                     category: .liqueur, volume: 40,  abv: 30.0, calories: 105, iconName: "drop.fill"),
        DrinkTemplate(name: "Drambuie",                     category: .liqueur, volume: 40,  abv: 40.0, calories: 105, iconName: "drop.fill"),
        DrinkTemplate(name: "Frangelico",                   category: .liqueur, volume: 40,  abv: 20.0, calories: 115, iconName: "drop.fill"),
        DrinkTemplate(name: "Amaretto Disaronno",           category: .liqueur, volume: 40,  abv: 28.0, calories: 135, iconName: "drop.fill"),
        DrinkTemplate(name: "Berentzen Apfelkorn",          category: .liqueur, volume: 40,  abv: 20.0, calories: 115, iconName: "drop.fill"),
        DrinkTemplate(name: "Raki",                         category: .liqueur, volume: 20,  abv: 45.0, calories: 60,  iconName: "drop.fill"),
        DrinkTemplate(name: "Saurer Apfel Schnapps",        category: .liqueur, volume: 40,  abv: 16.0, calories: 90,  iconName: "drop.fill"),
        DrinkTemplate(name: "Absinth",                      category: .liqueur, volume: 20,  abv: 55.0, calories: 75,  iconName: "drop.fill"),
        DrinkTemplate(name: "Advocaat",                     category: .liqueur, volume: 40,  abv: 14.0, calories: 125, iconName: "drop.fill"),
        DrinkTemplate(name: "Amarula",                      category: .liqueur, volume: 40,  abv: 17.0, calories: 115, iconName: "drop.fill"),
        DrinkTemplate(name: "Sheridan's",                   category: .liqueur, volume: 40,  abv: 15.5, calories: 125, iconName: "drop.fill"),
    ]

    // MARK: Shots

    private static let shots: [DrinkTemplate] = [
        DrinkTemplate(name: "Berliner Luft",                category: .shot, volume: 20, abv: 18.0, calories: 35,  iconName: "drop.fill"),
        DrinkTemplate(name: "Kümmerling",                   category: .shot, volume: 20, abv: 35.0, calories: 60,  iconName: "drop.fill"),
        DrinkTemplate(name: "Sourz Apple",                  category: .shot, volume: 20, abv: 15.0, calories: 38,  iconName: "drop.fill"),
        DrinkTemplate(name: "Sourz Raspberry",              category: .shot, volume: 20, abv: 15.0, calories: 38,  iconName: "drop.fill"),
        DrinkTemplate(name: "B-52",                         category: .shot, volume: 20, abv: 28.0, calories: 50,  iconName: "drop.fill"),
        DrinkTemplate(name: "Wodka Feige",                  category: .shot, volume: 20, abv: 20.0, calories: 42,  iconName: "drop.fill"),
        DrinkTemplate(name: "Tequila Sunrise Shot",         category: .shot, volume: 20, abv: 18.0, calories: 42,  iconName: "drop.fill"),
        DrinkTemplate(name: "Unicum",                       category: .shot, volume: 20, abv: 40.0, calories: 58,  iconName: "drop.fill"),
        DrinkTemplate(name: "Schwarzwald Shot",             category: .shot, volume: 20, abv: 35.0, calories: 55,  iconName: "drop.fill"),
        DrinkTemplate(name: "Baby Guinness",                category: .shot, volume: 20, abv: 25.0, calories: 45,  iconName: "drop.fill"),
        DrinkTemplate(name: "Tequila Slammer",              category: .shot, volume: 20, abv: 38.0, calories: 50,  iconName: "drop.fill"),
        DrinkTemplate(name: "Sourz Pink",                   category: .shot, volume: 20, abv: 15.0, calories: 38,  iconName: "drop.fill"),
    ]

    // MARK: Cocktails

    private static let cocktails: [DrinkTemplate] = [
        DrinkTemplate(name: "Aperol Spritz",                category: .cocktail, volume: 200, abv: 11.0, calories: 175, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Bloody Mary",                  category: .cocktail, volume: 200, abv: 10.0, calories: 150, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Caipirinha",                   category: .cocktail, volume: 150, abv: 16.0, calories: 240, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Cuba Libre",                   category: .cocktail, volume: 200, abv: 11.0, calories: 220, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Espresso Martini",             category: .cocktail, volume: 100, abv: 22.0, calories: 180, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Gin Tonic",                    category: .cocktail, volume: 200, abv: 12.0, calories: 180, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Hugo",                         category: .cocktail, volume: 200, abv: 9.0,  calories: 145, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Long Island Iced Tea",         category: .cocktail, volume: 250, abv: 22.0, calories: 350, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Mai Tai",                      category: .cocktail, volume: 200, abv: 22.0, calories: 290, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Manhattan",                    category: .cocktail, volume: 80,  abv: 30.0, calories: 150, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Margarita",                    category: .cocktail, volume: 150, abv: 18.0, calories: 280, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Mojito",                       category: .cocktail, volume: 200, abv: 11.0, calories: 210, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Moscow Mule",                  category: .cocktail, volume: 200, abv: 10.0, calories: 200, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Negroni",                      category: .cocktail, volume: 90,  abv: 24.0, calories: 160, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Old Fashioned",                category: .cocktail, volume: 80,  abv: 32.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Pina Colada",                  category: .cocktail, volume: 200, abv: 14.0, calories: 320, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sex on the Beach",             category: .cocktail, volume: 200, abv: 11.0, calories: 220, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Tequila Sunrise",              category: .cocktail, volume: 200, abv: 10.0, calories: 230, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Vodka Cola",                   category: .cocktail, volume: 200, abv: 10.0, calories: 200, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Vodka Energy",                 category: .cocktail, volume: 200, abv: 9.0,  calories: 195, iconName: "wineglass.fill"),

        // Klassiker
        DrinkTemplate(name: "Daiquiri",                     category: .cocktail, volume: 150, abv: 18.0, calories: 220, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Cosmopolitan",                 category: .cocktail, volume: 150, abv: 19.0, calories: 210, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Whiskey Sour",                 category: .cocktail, volume: 150, abv: 22.0, calories: 180, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Tom Collins",                  category: .cocktail, volume: 200, abv: 10.0, calories: 190, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Mint Julep",                   category: .cocktail, volume: 150, abv: 20.0, calories: 170, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Singapore Sling",              category: .cocktail, volume: 200, abv: 14.0, calories: 225, iconName: "wineglass.fill"),

        // Modern und trendig
        DrinkTemplate(name: "Gin Basil Smash",              category: .cocktail, volume: 200, abv: 25.0, calories: 215, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Bramble",                      category: .cocktail, volume: 150, abv: 18.0, calories: 175, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Pornstar Martini",             category: .cocktail, volume: 150, abv: 20.0, calories: 220, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Penicillin",                   category: .cocktail, volume: 120, abv: 22.0, calories: 150, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Paloma",                       category: .cocktail, volume: 200, abv: 12.0, calories: 175, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Watermelon Margarita",         category: .cocktail, volume: 200, abv: 16.0, calories: 225, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Espresso Tonic",               category: .cocktail, volume: 200, abv: 8.0,  calories: 125, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Aperol Tonic",                 category: .cocktail, volume: 200, abv: 7.0,  calories: 120, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lillet Tonic",                 category: .cocktail, volume: 200, abv: 6.0,  calories: 105, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Hugo Spritz",                  category: .cocktail, volume: 200, abv: 9.0,  calories: 145, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Caipirovka",                   category: .cocktail, volume: 150, abv: 16.0, calories: 235, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lillet Wild Berry Spritz",     category: .cocktail, volume: 200, abv: 8.0,  calories: 130, iconName: "wineglass.fill"),

        // Tropisch und süß
        DrinkTemplate(name: "Blue Lagoon",                  category: .cocktail, volume: 200, abv: 10.0, calories: 195, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Swimming Pool",                category: .cocktail, volume: 200, abv: 14.0, calories: 235, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Hurricane",                    category: .cocktail, volume: 200, abv: 14.0, calories: 245, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Zombie",                       category: .cocktail, volume: 150, abv: 28.0, calories: 275, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Bahama Mama",                  category: .cocktail, volume: 200, abv: 10.0, calories: 195, iconName: "wineglass.fill"),

        // Punsch und Bowle
        DrinkTemplate(name: "Eierpunsch",                   category: .cocktail, volume: 200, abv: 8.0,  calories: 205, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Feuerzangenbowle",             category: .cocktail, volume: 200, abv: 12.0, calories: 225, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Maibowle",                     category: .cocktail, volume: 200, abv: 8.0,  calories: 185, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Erdbeerbowle",                 category: .cocktail, volume: 200, abv: 7.0,  calories: 165, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Pfirsichbowle",                category: .cocktail, volume: 200, abv: 8.0,  calories: 175, iconName: "wineglass.fill"),

        // Weitere Klassiker
        DrinkTemplate(name: "Mojito Rosa",                  category: .cocktail, volume: 200, abv: 12.0, calories: 215, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Caipiroska",                   category: .cocktail, volume: 150, abv: 16.0, calories: 235, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Gin Garden",                   category: .cocktail, volume: 200, abv: 12.0, calories: 185, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Rhubarb Spritz",               category: .cocktail, volume: 200, abv: 9.0,  calories: 150, iconName: "wineglass.fill"),
    ]

    // MARK: Fertige Mischgetränke

    private static let mischgetraenke: [DrinkTemplate] = [
        DrinkTemplate(name: "Bacardi Breezer Lime",         category: .mixed, volume: 275, abv: 5.5,  calories: 185, iconName: "mug.fill"),
        DrinkTemplate(name: "Jim Beam Cola",                category: .mixed, volume: 330, abv: 10.0, calories: 215, iconName: "mug.fill"),
        DrinkTemplate(name: "Jack Daniel's Cola",           category: .mixed, volume: 330, abv: 10.0, calories: 220, iconName: "mug.fill"),
        DrinkTemplate(name: "Wodka Energy Dose",            category: .mixed, volume: 250, abv: 7.0,  calories: 150, iconName: "mug.fill"),
        DrinkTemplate(name: "Smirnoff Ice Cranberry",       category: .mixed, volume: 330, abv: 4.0,  calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "V+ Cola",                      category: .mixed, volume: 330, abv: 10.0, calories: 215, iconName: "mug.fill"),
        DrinkTemplate(name: "Berentzen Mix",                category: .mixed, volume: 330, abv: 16.0, calories: 345, iconName: "mug.fill"),
        DrinkTemplate(name: "Tequila Sunrise Dose",         category: .mixed, volume: 330, abv: 5.5,  calories: 175, iconName: "mug.fill"),
        DrinkTemplate(name: "Schwarze Sau",                 category: .mixed, volume: 330, abv: 17.0, calories: 295, iconName: "mug.fill"),
    ]

    // MARK: Cider

    private static let cider: [DrinkTemplate] = [
        DrinkTemplate(name: "Strongbow Original",           category: .cider, volume: 500, abv: 4.5, calories: 205, iconName: "mug.fill"),
        DrinkTemplate(name: "Kopparberg Pear",              category: .cider, volume: 330, abv: 4.5, calories: 148, iconName: "mug.fill"),
        DrinkTemplate(name: "Somersby Apple",               category: .cider, volume: 330, abv: 4.5, calories: 155, iconName: "mug.fill"),
        DrinkTemplate(name: "Rekorderlig Wildberries",      category: .cider, volume: 330, abv: 4.5, calories: 148, iconName: "mug.fill"),
        DrinkTemplate(name: "Old Mout Berries",             category: .cider, volume: 330, abv: 4.0, calories: 140, iconName: "mug.fill"),
        DrinkTemplate(name: "Kopparberg Strawberry Lime",   category: .cider, volume: 330, abv: 4.5, calories: 152, iconName: "mug.fill"),

        // Hard Seltzer
        DrinkTemplate(name: "White Claw Mango",             category: .cider, volume: 330, abv: 5.0, calories: 100, iconName: "cylinder.fill"),
        DrinkTemplate(name: "White Claw Black Cherry",      category: .cider, volume: 330, abv: 5.0, calories: 100, iconName: "cylinder.fill"),
        DrinkTemplate(name: "White Claw Natural Lime",      category: .cider, volume: 330, abv: 5.0, calories: 100, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Truly Wild Berry",             category: .cider, volume: 355, abv: 5.0, calories: 100, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Aldi Hard Seltzer",            category: .cider, volume: 330, abv: 4.8, calories: 96,  iconName: "cylinder.fill"),
    ]

    // MARK: Likoerweine

    private static let likoerweine: [DrinkTemplate] = [
        DrinkTemplate(name: "Sherry Fino",                  category: .fortified, volume: 100, abv: 15.0, calories: 115, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sherry Amontillado",           category: .fortified, volume: 100, abv: 17.0, calories: 130, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Sherry Oloroso",               category: .fortified, volume: 100, abv: 18.0, calories: 135, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Portwein Tawny",               category: .fortified, volume: 100, abv: 20.0, calories: 153, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Portwein Ruby",                category: .fortified, volume: 100, abv: 20.0, calories: 163, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Madeira",                      category: .fortified, volume: 100, abv: 18.0, calories: 140, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Marsala",                      category: .fortified, volume: 100, abv: 17.0, calories: 130, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Vermouth Rosso",               category: .fortified, volume: 100, abv: 15.0, calories: 105, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Vermouth Bianco",              category: .fortified, volume: 100, abv: 15.0, calories: 100, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Vermouth Extra Dry",           category: .fortified, volume: 100, abv: 18.0, calories: 115, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lillet Blanc",                 category: .fortified, volume: 100, abv: 17.0, calories: 120, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Cinzano Bianco",               category: .fortified, volume: 100, abv: 14.4, calories: 100, iconName: "wineglass.fill"),
    ]

    // MARK: CSV-Sortiment (v5)
    // Vollständiges Getränkesortiment aus komplettes_getraenkesortiment_exakt.csv.
    // Kalorien-Formeln: Bier = vol*abv*0.086, Radler/Mix(low) = vol*0.50,
    //   Mix(high) = vol*abv*0.086, Premix-Dose = vol*abv*0.065,
    //   Spirituosen (40 ml) = 40*abv*0.055, Likör (20 ml) = 20*abv*0.062,
    //   Aperitif (50 ml) = 50*abv*0.060, Hugo (200 ml) = 200*abv*0.082+60.
    // Duplikate werden von seedIfNeeded automatisch übersprungen (Name-Match).

    private static let csvSortiment: [DrinkTemplate] = [

        // MARK: Pils
        DrinkTemplate(name: "Krombacher Pils",                  category: .beer,  volume: 330, abv: 4.8, calories: 136, iconName: "mug.fill"),
        DrinkTemplate(name: "Krombacher Pils Alkoholfrei",      category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Krombacher Weizen",                category: .beer,  volume: 500, abv: 5.3, calories: 228, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Bitburger Premium Pils",           category: .beer,  volume: 330, abv: 4.8, calories: 136, iconName: "mug.fill"),
        DrinkTemplate(name: "Bitburger 0,0% Alkoholfrei",       category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Veltins Pilsener",                 category: .beer,  volume: 330, abv: 4.8, calories: 136, iconName: "mug.fill"),
        DrinkTemplate(name: "Veltins Helles Pülleken",          category: .beer,  volume: 330, abv: 5.2, calories: 147, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Beck's Pils",                      category: .beer,  volume: 330, abv: 4.9, calories: 139, iconName: "mug.fill"),
        DrinkTemplate(name: "Beck's Gold",                      category: .beer,  volume: 330, abv: 4.9, calories: 139, iconName: "mug.fill"),
        DrinkTemplate(name: "Beck's Blue Alkoholfrei",          category: .other, volume: 330, abv: 0.3, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Warsteiner Premium Pilsener",      category: .beer,  volume: 330, abv: 4.8, calories: 136, iconName: "mug.fill"),
        DrinkTemplate(name: "Warsteiner Herb",                  category: .beer,  volume: 330, abv: 4.8, calories: 136, iconName: "mug.fill"),
        DrinkTemplate(name: "Radeberger Pilsner",               category: .beer,  volume: 330, abv: 4.8, calories: 136, iconName: "mug.fill"),
        DrinkTemplate(name: "Jever Fun Alkoholfrei",            category: .other, volume: 330, abv: 0.3, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Oettinger Pils",                   category: .beer,  volume: 500, abv: 4.7, calories: 202, iconName: "mug.fill"),
        DrinkTemplate(name: "Oettinger Export",                 category: .beer,  volume: 500, abv: 5.4, calories: 232, iconName: "mug.fill"),
        DrinkTemplate(name: "Hasseröder Premium Pils",          category: .beer,  volume: 500, abv: 4.9, calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "König Pilsener",                   category: .beer,  volume: 330, abv: 4.9, calories: 139, iconName: "mug.fill"),
        DrinkTemplate(name: "Holsten Pilsener",                 category: .beer,  volume: 500, abv: 4.8, calories: 206, iconName: "mug.fill"),

        // MARK: Helles / Export
        DrinkTemplate(name: "Augustiner Lagerbier Hell",        category: .beer,  volume: 500, abv: 5.2, calories: 223, iconName: "mug.fill"),
        DrinkTemplate(name: "Paulaner Münchner Hell",           category: .beer,  volume: 500, abv: 4.9, calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Benediktiner Hell",                category: .beer,  volume: 500, abv: 5.0, calories: 215, iconName: "mug.fill"),
        DrinkTemplate(name: "Benediktiner Weissbier",           category: .beer,  volume: 500, abv: 5.4, calories: 232, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Chiemseer Hell",                   category: .beer,  volume: 500, abv: 4.8, calories: 206, iconName: "mug.fill"),
        DrinkTemplate(name: "Mönchshof Original",               category: .beer,  volume: 500, abv: 4.9, calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Bayreuther Brauhaus Hell",         category: .beer,  volume: 500, abv: 4.9, calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Grevensteiner Original",           category: .beer,  volume: 330, abv: 5.2, calories: 147, iconName: "mug.fill"),
        DrinkTemplate(name: "Zirndorfer Landbier",              category: .beer,  volume: 500, abv: 4.9, calories: 210, iconName: "mug.fill"),

        // MARK: Weizenbier
        DrinkTemplate(name: "Paulaner Hefe-Weißbier Naturtrüb",       category: .beer,  volume: 500, abv: 5.5, calories: 236, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Paulaner Hefe-Weißbier Alkoholfrei",     category: .other, volume: 500, abv: 0.5, calories: 60,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Franziskaner Hefe-Weissbier Naturtrüb",  category: .beer,  volume: 500, abv: 5.0, calories: 215, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Franziskaner Alkoholfrei",                category: .other, volume: 500, abv: 0.5, calories: 60,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Maisel's Weisse Original",                category: .beer,  volume: 500, abv: 5.2, calories: 223, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Störtebeker Bernstein-Weizen",            category: .beer,  volume: 500, abv: 5.3, calories: 228, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Erdinger Alkoholfrei Zitrone",            category: .other, volume: 330, abv: 0.3, calories: 40,  iconName: "wineglass.fill"),

        // MARK: Kellerbier / Craft Beer / Spezial
        DrinkTemplate(name: "Mönchshof Kellerbier",             category: .beer,  volume: 500, abv: 5.4, calories: 232, iconName: "mug.fill"),
        DrinkTemplate(name: "Störtebeker Atlantik-Ale",         category: .beer,  volume: 330, abv: 5.1, calories: 144, iconName: "mug.fill"),
        DrinkTemplate(name: "Störtebeker Keller-Bier 1402",     category: .beer,  volume: 500, abv: 4.8, calories: 206, iconName: "mug.fill"),
        DrinkTemplate(name: "Störtebeker Pilsener-Bier",        category: .beer,  volume: 500, abv: 4.9, calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Störtebeker Frei-Bier Alkoholfrei",category: .other, volume: 330, abv: 0.5, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Ratsherrn Pale Ale",               category: .beer,  volume: 330, abv: 5.6, calories: 158, iconName: "mug.fill"),
        DrinkTemplate(name: "Rügener Baltic Ale",               category: .beer,  volume: 330, abv: 7.5, calories: 212, iconName: "mug.fill"),
        DrinkTemplate(name: "Crew Republic Drunken Sailor IPA", category: .beer,  volume: 330, abv: 6.4, calories: 181, iconName: "mug.fill"),
        DrinkTemplate(name: "Leffe Blonde",                     category: .beer,  volume: 330, abv: 6.6, calories: 187, iconName: "mug.fill"),
        DrinkTemplate(name: "Leffe Brune",                      category: .beer,  volume: 330, abv: 6.5, calories: 184, iconName: "mug.fill"),
        DrinkTemplate(name: "Kilkenny Irish Red Ale",           category: .beer,  volume: 330, abv: 4.3, calories: 122, iconName: "mug.fill"),
        DrinkTemplate(name: "Neumarkter Lammsbräu Bio Glutenfrei", category: .beer, volume: 330, abv: 4.7, calories: 133, iconName: "mug.fill"),
        DrinkTemplate(name: "Neumarkter Lammsbräu Urstoff Bio", category: .beer,  volume: 500, abv: 4.7, calories: 202, iconName: "mug.fill"),
        DrinkTemplate(name: "Estrella Galicia Especial",        category: .beer,  volume: 330, abv: 5.5, calories: 156, iconName: "mug.fill"),
        DrinkTemplate(name: "Carlsberg Elephant",               category: .beer,  volume: 330, abv: 7.5, calories: 212, iconName: "mug.fill"),
        DrinkTemplate(name: "Carlsberg Pilsner",                category: .beer,  volume: 330, abv: 5.0, calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Heineken Premium Lager",           category: .beer,  volume: 330, abv: 5.0, calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Heineken 0.0%",                    category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Corona Cero 0.0%",                 category: .other, volume: 355, abv: 0.0, calories: 42,  iconName: "mug.fill"),

        // MARK: Kölsch
        DrinkTemplate(name: "Früh Kölsch",                      category: .beer,  volume: 200, abv: 4.8, calories: 82,  iconName: "cylinder.fill"),

        // MARK: Altbier
        DrinkTemplate(name: "Frankenheim Alt",                  category: .beer,  volume: 330, abv: 4.8, calories: 136, iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Bolten Alt",                       category: .beer,  volume: 330, abv: 4.9, calories: 139, iconName: "cup.and.saucer.fill"),

        // MARK: Stout
        DrinkTemplate(name: "Guinness Extra Stout",             category: .beer,  volume: 330, abv: 4.1, calories: 116, iconName: "mug.fill"),
        DrinkTemplate(name: "Guinness Hop House 13",            category: .beer,  volume: 330, abv: 5.0, calories: 142, iconName: "mug.fill"),

        // MARK: Malzbier
        DrinkTemplate(name: "Vitamalz Das Original",            category: .other, volume: 330, abv: 0.0, calories: 50,  iconName: "mug.fill"),
        DrinkTemplate(name: "Karamalz Classic",                 category: .other, volume: 330, abv: 0.2, calories: 55,  iconName: "mug.fill"),
        DrinkTemplate(name: "Kandi Malz",                       category: .other, volume: 330, abv: 0.0, calories: 50,  iconName: "mug.fill"),
        DrinkTemplate(name: "Oettinger Malz",                   category: .other, volume: 500, abv: 0.0, calories: 75,  iconName: "mug.fill"),
        DrinkTemplate(name: "Sünner Malz",                      category: .other, volume: 330, abv: 0.0, calories: 50,  iconName: "mug.fill"),

        // MARK: Radler
        DrinkTemplate(name: "Warsteiner Radler Zitrone",        category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Oettinger Radler",                 category: .mixed, volume: 500, abv: 2.5, calories: 250, iconName: "mug.fill"),
        DrinkTemplate(name: "Gösser NaturRadler",               category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Gösser NaturRadler Alkoholfrei",   category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Mönchshof Natur Radler",           category: .mixed, volume: 500, abv: 2.5, calories: 250, iconName: "mug.fill"),
        DrinkTemplate(name: "Mönchshof Natur Radler Alkoholfrei", category: .other, volume: 500, abv: 0.5, calories: 60, iconName: "mug.fill"),
        DrinkTemplate(name: "Krombacher Radler Naturtrüb",      category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Bitburger Radler Naturtrüb",       category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Hacker-Pschorr Natur Radler",      category: .mixed, volume: 500, abv: 2.5, calories: 250, iconName: "mug.fill"),
        DrinkTemplate(name: "Zötler Naturradler",               category: .mixed, volume: 500, abv: 2.5, calories: 250, iconName: "mug.fill"),
        DrinkTemplate(name: "Paulaner Weißbier-Zitrone",        category: .mixed, volume: 500, abv: 2.5, calories: 250, iconName: "wineglass.fill"),

        // MARK: Biermischgetränke
        DrinkTemplate(name: "Veltins V+ Lemon",                 category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Veltins V+ Curuba",                category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Veltins V+ Cola",                  category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Veltins V+ Energy",                category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Veltins V+ Apple",                 category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Veltins V+ Berry-X",               category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Veltins V+ Grapefruit",            category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Veltins V+ Sprizz",                category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Karlsberg Mixery Bier+Cola+X",     category: .mixed, volume: 330, abv: 3.1, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Karlsberg Mixery Bier+Cherry+X",   category: .mixed, volume: 330, abv: 3.1, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Karlsberg Mixery Iced Lemon",      category: .mixed, volume: 330, abv: 5.0, calories: 142, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Karlsberg Mixery Iced Blue",       category: .mixed, volume: 330, abv: 5.0, calories: 142, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Astra Rotlicht",                   category: .beer,  volume: 330, abv: 6.0, calories: 170, iconName: "mug.fill"),
        DrinkTemplate(name: "Astra Rakete",                     category: .mixed, volume: 330, abv: 5.9, calories: 167, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Astra Granate Energy",             category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Astra Nackt",                      category: .other, volume: 330, abv: 0.5, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Gaffel Lemon",                     category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Gaffel Kölsch & Cola",             category: .mixed, volume: 330, abv: 2.0, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Gaffel Fassbrause Zitrone",        category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Schöfferhofer Grapefruit",         category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Schöfferhofer Grapefruit Alkoholfrei", category: .other, volume: 330, abv: 0.0, calories: 40, iconName: "mug.fill"),
        DrinkTemplate(name: "Schöfferhofer Granatapfel",        category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Schöfferhofer Maracuja",           category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Schöfferhofer Zitrone Naturtrüb",  category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Schöfferhofer Weizen-Mix Kirsche", category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Schöfferhofer Kaktusfeige",        category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Desperados Original",              category: .mixed, volume: 330, abv: 5.9, calories: 167, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Desperados Mojito",                category: .mixed, volume: 330, abv: 5.9, calories: 167, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Desperados Red",                   category: .mixed, volume: 330, abv: 5.9, calories: 167, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Desperados Lime",                  category: .mixed, volume: 330, abv: 3.0, calories: 165, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Desperados 0.0%",                  category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Beck's Green Lemon",               category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Beck's Green Lemon Zero",          category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "mug.fill"),
        DrinkTemplate(name: "Beck's Ice",                       category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Beck's Red Holunder",              category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Beck's Lemon Brew",                category: .mixed, volume: 330, abv: 2.5, calories: 165, iconName: "mug.fill"),
        DrinkTemplate(name: "Salitos Original",                 category: .mixed, volume: 330, abv: 5.9, calories: 167, iconName: "mug.fill"),
        DrinkTemplate(name: "Salitos Ice",                      category: .mixed, volume: 330, abv: 5.2, calories: 147, iconName: "mug.fill"),
        DrinkTemplate(name: "Salitos Blue",                     category: .mixed, volume: 330, abv: 5.0, calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Salitos Pink",                     category: .mixed, volume: 330, abv: 5.0, calories: 142, iconName: "mug.fill"),
        DrinkTemplate(name: "Salitos 0,0%",                     category: .other, volume: 330, abv: 0.0, calories: 40,  iconName: "mug.fill"),

        // MARK: Hugo / Weinhaltiges Getränk (200 ml Glas-Serving)
        DrinkTemplate(name: "Käfer Hugo",                       category: .sparkling, volume: 200, abv: 6.9, calories: 173, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Scavi & Ray Hugo",                 category: .sparkling, volume: 200, abv: 6.0, calories: 158, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Light live Hugo Alkoholfrei",      category: .other,     volume: 200, abv: 0.0, calories: 40,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Jules Mumm Hugo",                  category: .sparkling, volume: 200, abv: 8.0, calories: 191, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Mionetto Il Hugo",                 category: .sparkling, volume: 200, abv: 8.0, calories: 191, iconName: "wineglass.fill"),

        // MARK: Premix-Dose (330 ml, cylinder.fill)
        DrinkTemplate(name: "Jack Daniel's Berry",              category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Jack Daniel's Ginger",             category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Jim Beam Ice Tea",                 category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Gordon's Gin & Tonic",             category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Bombay Sapphire Gin & Tonic",      category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Smirnoff Ice Original",            category: .mixed, volume: 250, abv: 4.0,  calories: 65,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Smirnoff Ice Tropical",            category: .mixed, volume: 250, abv: 4.0,  calories: 65,  iconName: "cylinder.fill"),
        DrinkTemplate(name: "Bacardi Razz & Sprite",            category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Bacardi Mojito Dose",              category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Pitu Caipirinha Dose",             category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Absolut Mixt Blueberry & Lime",    category: .mixed, volume: 330, abv: 5.0,  calories: 107, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Absolut Mixt Cloudberry & Apple",  category: .mixed, volume: 330, abv: 5.0,  calories: 107, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Three Sixty Vodka Mate",           category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "9 Mile Vodka Energy",              category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Effect Vodka Energy",              category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Captain Morgan Mojito Dose",       category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Wodka Gorbatschow Lemon Dose",     category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Wodka Gorbatschow Energy Dose",    category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Wodka Gorbatschow Maracuja Dose",  category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Sierra Tequila Margarita Dose",    category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Kleiner Feigling Coco Biscuit Dose", category: .mixed, volume: 250, abv: 10.0, calories: 162, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Havana Club Cola Dose",            category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Malibu Pineapple Dose",            category: .mixed, volume: 330, abv: 10.0, calories: 214, iconName: "cylinder.fill"),

        // MARK: Aperitif (50 ml Serving, wineglass.fill)
        DrinkTemplate(name: "Aperol Aperitivo",                 category: .fortified, volume: 50, abv: 11.0, calories: 33,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Campari Bitter",                   category: .fortified, volume: 50, abv: 25.0, calories: 75,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lillet Rose",                      category: .fortified, volume: 50, abv: 17.0, calories: 51,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lillet Rouge",                     category: .fortified, volume: 50, abv: 17.0, calories: 51,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Martini Bianco",                   category: .fortified, volume: 75, abv: 14.4, calories: 64,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Martini Rosso",                    category: .fortified, volume: 75, abv: 14.4, calories: 64,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Martini Extra Dry",                category: .fortified, volume: 75, abv: 15.0, calories: 67,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Martini Fiero",                    category: .fortified, volume: 75, abv: 14.9, calories: 67,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Ramazzotti Aperitivo Rosato",      category: .fortified, volume: 50, abv: 15.0, calories: 45,  iconName: "wineglass.fill"),
        DrinkTemplate(name: "Pimm's No. 1",                     category: .fortified, volume: 50, abv: 25.0, calories: 75,  iconName: "wineglass.fill"),

        // MARK: Whiskey / Whisky (40 ml Serving, drop.fill)
        DrinkTemplate(name: "Jack Daniel's Old No. 7",          category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Jack Daniel's Gentleman Jack",     category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Jack Daniel's Single Barrel",      category: .spirits, volume: 40, abv: 45.0, calories: 99, iconName: "drop.fill"),
        DrinkTemplate(name: "Jack Daniel's Tennessee Honey",    category: .liqueur, volume: 20, abv: 35.0, calories: 43, iconName: "drop.fill"),
        DrinkTemplate(name: "Jim Beam Bourbon Whiskey",         category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Jim Beam Red Stag",                category: .liqueur, volume: 20, abv: 32.5, calories: 40, iconName: "drop.fill"),
        DrinkTemplate(name: "Jim Beam Apple",                   category: .liqueur, volume: 20, abv: 32.5, calories: 40, iconName: "drop.fill"),
        DrinkTemplate(name: "Johnnie Walker Red Label",         category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Johnnie Walker Black Label",       category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Jameson Irish Whiskey",            category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Tullamore D.E.W. Irish Whiskey",   category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Chivas Regal 12 Years",            category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Glenfiddich 12 Years Single Malt", category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Maker's Mark Bourbon",             category: .spirits, volume: 40, abv: 45.0, calories: 99, iconName: "drop.fill"),
        DrinkTemplate(name: "Lagavulin 16 Years Single Malt",   category: .spirits, volume: 40, abv: 43.0, calories: 94, iconName: "drop.fill"),
        DrinkTemplate(name: "Laphroaig 10 Years Single Malt",   category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),

        // MARK: Vodka (40 ml)
        DrinkTemplate(name: "Absolut Vodka Blue",               category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Absolut Citron",                   category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Absolut Kurant",                   category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Smirnoff Red Label No. 21",        category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Three Sixty Vodka",                category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Three Sixty Black 42",             category: .spirits, volume: 40, abv: 42.0, calories: 92, iconName: "drop.fill"),
        DrinkTemplate(name: "Wodka Gorbatschow Vodka",          category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "9 Mile Vodka",                     category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Belvedere Vodka",                  category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Grey Goose Vodka",                 category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Finlandia Vodka",                  category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Skyy Vodka",                       category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),

        // MARK: Gin (40 ml)
        DrinkTemplate(name: "Gordon's London Dry Gin",          category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Gordon's Pink Gin",                category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Gordon's Sicilian Lemon",          category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Bombay Sapphire London Dry Gin",   category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Tanqueray London Dry Gin",         category: .spirits, volume: 40, abv: 43.1, calories: 94, iconName: "drop.fill"),
        DrinkTemplate(name: "Tanqueray Flor de Sevilla",        category: .spirits, volume: 40, abv: 41.3, calories: 90, iconName: "drop.fill"),
        DrinkTemplate(name: "Hendrick's Gin",                   category: .spirits, volume: 40, abv: 41.4, calories: 91, iconName: "drop.fill"),
        DrinkTemplate(name: "Monkey 47 Schwarzwald Dry Gin",    category: .spirits, volume: 40, abv: 47.0, calories: 103, iconName: "drop.fill"),
        DrinkTemplate(name: "Beefeater London Dry Gin",         category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Gin Sul Dry Gin",                  category: .spirits, volume: 40, abv: 43.0, calories: 94, iconName: "drop.fill"),
        DrinkTemplate(name: "Malfy Gin Rosa",                   category: .spirits, volume: 40, abv: 41.0, calories: 90, iconName: "drop.fill"),
        DrinkTemplate(name: "Malfy Gin Limone",                 category: .spirits, volume: 40, abv: 41.0, calories: 90, iconName: "drop.fill"),

        // MARK: Rum (40 ml)
        DrinkTemplate(name: "Bacardi Carta Blanca",             category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Bacardi Carta Oro",                category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Bacardi Carta Negra",              category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Bacardi Oakheart Spiced",          category: .spirits, volume: 40, abv: 35.0, calories: 77, iconName: "drop.fill"),
        DrinkTemplate(name: "Havana Club 3 Años",               category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Havana Club 7 Años",               category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Havana Club Especial",             category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Captain Morgan Spiced Gold",       category: .spirits, volume: 40, abv: 35.0, calories: 77, iconName: "drop.fill"),
        DrinkTemplate(name: "Captain Morgan White Rum",         category: .spirits, volume: 40, abv: 37.5, calories: 82, iconName: "drop.fill"),
        DrinkTemplate(name: "Ron Zacapa Centenario 23",         category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Botucal Reserva Exclusiva",        category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Don Papa Rum",                     category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Plantation Pineapple Rum",         category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),

        // MARK: Weinbrand / Cognac / Brandy (40 ml)
        DrinkTemplate(name: "Asbach Uralt",                     category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "Chantré Weinbrand",                category: .spirits, volume: 40, abv: 36.0, calories: 79, iconName: "drop.fill"),
        DrinkTemplate(name: "Mariacron Weinbrand",              category: .spirits, volume: 40, abv: 36.0, calories: 79, iconName: "drop.fill"),
        DrinkTemplate(name: "Wilthener Goldkrone",              category: .spirits, volume: 40, abv: 28.0, calories: 61, iconName: "drop.fill"),
        DrinkTemplate(name: "Hennessy V.S Cognac",              category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Rémy Martin V.S.O.P Cognac",       category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Osborne Veterano Brandy",          category: .spirits, volume: 40, abv: 30.0, calories: 66, iconName: "drop.fill"),
        DrinkTemplate(name: "Osborne 103 Brandy",               category: .spirits, volume: 40, abv: 30.0, calories: 66, iconName: "drop.fill"),

        // MARK: Anis / Korn / Grappa / Obstbrand / Tequila / Cachaca (40 ml)
        DrinkTemplate(name: "Ouzo 12",                          category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "Pernod Anis",                      category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Ricard Pastis",                    category: .spirits, volume: 40, abv: 45.0, calories: 99, iconName: "drop.fill"),
        DrinkTemplate(name: "Nordhäuser Doppelkorn",            category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "Nordhäuser Eiskorn",               category: .spirits, volume: 40, abv: 32.0, calories: 70, iconName: "drop.fill"),
        DrinkTemplate(name: "Berentzen Korn",                   category: .spirits, volume: 40, abv: 32.0, calories: 70, iconName: "drop.fill"),
        DrinkTemplate(name: "Strothmann Weizenkorn",            category: .spirits, volume: 40, abv: 32.0, calories: 70, iconName: "drop.fill"),
        DrinkTemplate(name: "Oldesloer Weizenkorn",             category: .spirits, volume: 40, abv: 32.0, calories: 70, iconName: "drop.fill"),
        DrinkTemplate(name: "Julia Grappa",                     category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "Nonino Grappa Vendemmia",          category: .spirits, volume: 40, abv: 41.0, calories: 90, iconName: "drop.fill"),
        DrinkTemplate(name: "Schladerer Kirschwasser",          category: .spirits, volume: 40, abv: 42.0, calories: 92, iconName: "drop.fill"),
        DrinkTemplate(name: "Schladerer Williams-Birne",        category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Schladerer Himbeergeist",          category: .spirits, volume: 40, abv: 42.0, calories: 92, iconName: "drop.fill"),
        DrinkTemplate(name: "Prinz Williams-Christ-Birne",      category: .spirits, volume: 40, abv: 41.0, calories: 90, iconName: "drop.fill"),
        DrinkTemplate(name: "Prinz Alte Marille",               category: .spirits, volume: 40, abv: 41.0, calories: 90, iconName: "drop.fill"),
        DrinkTemplate(name: "Lantenhammer Haselnuss",           category: .spirits, volume: 40, abv: 42.0, calories: 92, iconName: "drop.fill"),
        DrinkTemplate(name: "Sierra Tequila Silver",            category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "Sierra Tequila Reposado",          category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "Jose Cuervo Especial Silver",      category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "Jose Cuervo Especial Reposado",    category: .spirits, volume: 40, abv: 38.0, calories: 83, iconName: "drop.fill"),
        DrinkTemplate(name: "San Cosme Mezcal Artesanal",       category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Pitu Cachaca",                     category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),
        DrinkTemplate(name: "Nega Fulo Cachaca",                category: .spirits, volume: 40, abv: 41.5, calories: 91, iconName: "drop.fill"),
        DrinkTemplate(name: "Janeiro Cachaca",                  category: .spirits, volume: 40, abv: 40.0, calories: 88, iconName: "drop.fill"),

        // MARK: Likör (20 ml Standard; Baileys/Amarula 50 ml)
        DrinkTemplate(name: "Jägermeister Kräuterlikör",        category: .liqueur, volume: 20, abv: 35.0, calories: 43, iconName: "drop.fill"),
        DrinkTemplate(name: "Jägermeister Scharf",              category: .liqueur, volume: 20, abv: 33.0, calories: 41, iconName: "drop.fill"),
        DrinkTemplate(name: "Baileys Original Irish Cream",     category: .liqueur, volume: 50, abv: 17.0, calories: 52, iconName: "drop.fill"),
        DrinkTemplate(name: "Baileys Salted Caramel",           category: .liqueur, volume: 50, abv: 17.0, calories: 52, iconName: "drop.fill"),
        DrinkTemplate(name: "Ramazzotti Amaro",                 category: .liqueur, volume: 20, abv: 30.0, calories: 37, iconName: "drop.fill"),
        DrinkTemplate(name: "Kahlua Kaffeelikör",               category: .liqueur, volume: 20, abv: 20.0, calories: 25, iconName: "drop.fill"),
        DrinkTemplate(name: "Malibu Coconut",                   category: .liqueur, volume: 20, abv: 21.0, calories: 26, iconName: "drop.fill"),
        DrinkTemplate(name: "Licor 43 Cuarenta Y Tres",         category: .liqueur, volume: 20, abv: 31.0, calories: 38, iconName: "drop.fill"),
        DrinkTemplate(name: "Cointreau Orangenlikör",           category: .liqueur, volume: 20, abv: 40.0, calories: 50, iconName: "drop.fill"),
        DrinkTemplate(name: "Disaronno Amaretto",               category: .liqueur, volume: 20, abv: 28.0, calories: 35, iconName: "drop.fill"),
        DrinkTemplate(name: "Southern Comfort Likör",           category: .liqueur, volume: 20, abv: 35.0, calories: 43, iconName: "drop.fill"),
        DrinkTemplate(name: "Batida de Coco",                   category: .liqueur, volume: 20, abv: 16.0, calories: 20, iconName: "drop.fill"),
        DrinkTemplate(name: "Berentzen Apfel",                  category: .liqueur, volume: 20, abv: 18.0, calories: 22, iconName: "drop.fill"),
        DrinkTemplate(name: "Berentzen Saurer Apfel",           category: .liqueur, volume: 20, abv: 16.0, calories: 20, iconName: "drop.fill"),
        DrinkTemplate(name: "Berentzen Waldmeister",            category: .liqueur, volume: 20, abv: 16.0, calories: 20, iconName: "drop.fill"),
        DrinkTemplate(name: "Kuemmerling Kräuterlikör",         category: .liqueur, volume: 20, abv: 35.0, calories: 43, iconName: "drop.fill"),
        DrinkTemplate(name: "Kleiner Feigling Original",        category: .liqueur, volume: 20, abv: 20.0, calories: 25, iconName: "drop.fill"),
        DrinkTemplate(name: "Amarula Cream Liqueur",            category: .liqueur, volume: 50, abv: 17.0, calories: 52, iconName: "drop.fill"),
        DrinkTemplate(name: "Eckes Edelkirsch",                 category: .liqueur, volume: 20, abv: 20.0, calories: 25, iconName: "drop.fill"),
        DrinkTemplate(name: "Pfeffi Pfefferminzlikör",          category: .liqueur, volume: 20, abv: 18.0, calories: 22, iconName: "drop.fill"),
        DrinkTemplate(name: "Berliner Luft Pfefferminzlikör",   category: .liqueur, volume: 20, abv: 18.0, calories: 22, iconName: "drop.fill"),
        DrinkTemplate(name: "Averna Amaro",                     category: .liqueur, volume: 20, abv: 29.0, calories: 36, iconName: "drop.fill"),
        DrinkTemplate(name: "Bols Peppermint",                  category: .liqueur, volume: 20, abv: 24.0, calories: 30, iconName: "drop.fill"),
        DrinkTemplate(name: "Bols Blue Curacao",                category: .liqueur, volume: 20, abv: 21.0, calories: 26, iconName: "drop.fill"),
        DrinkTemplate(name: "Killepitsch Kräuterlikör",         category: .liqueur, volume: 20, abv: 42.0, calories: 52, iconName: "drop.fill"),
        DrinkTemplate(name: "Underberg Kräuterlikör",           category: .liqueur, volume: 20, abv: 44.0, calories: 55, iconName: "drop.fill"),
    ]

    // MARK: Neue Getränke (v4)

    private static let neueDrinks: [DrinkTemplate] = [

        // Altbier (Düsseldorf, served in short 300ml Alt-Becher → cup.and.saucer.fill)
        DrinkTemplate(name: "Uerige Altbier",               category: .beer, volume: 300, abv: 4.7,  calories: 128, iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Schlüssel Alt",                category: .beer, volume: 300, abv: 4.8,  calories: 130, iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Schumacher Alt",               category: .beer, volume: 300, abv: 4.7,  calories: 128, iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Füchschen Alt",                category: .beer, volume: 300, abv: 4.8,  calories: 130, iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Diebels Alt",                  category: .beer, volume: 330, abv: 4.9,  calories: 142, iconName: "cup.and.saucer.fill"),

        // Bockbier
        DrinkTemplate(name: "Spaten Optimator",             category: .beer, volume: 500, abv: 7.2,  calories: 300, iconName: "mug.fill"),
        DrinkTemplate(name: "Weihenstephaner Korbinian",    category: .beer, volume: 500, abv: 7.4,  calories: 310, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Einbecker Ur-Bock Dunkel",     category: .beer, volume: 330, abv: 6.5,  calories: 175, iconName: "mug.fill"),

        // Dosenbier / Flaschen (cylinder = can)
        DrinkTemplate(name: "Becks Bier Dose",              category: .beer, volume: 500, abv: 4.9,  calories: 205, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Sternburg Export Dose",        category: .beer, volume: 500, abv: 4.9,  calories: 205, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Astra Rotlicht Dose",          category: .beer, volume: 500, abv: 5.0,  calories: 210, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Oettinger Pils Dose",          category: .beer, volume: 500, abv: 4.7,  calories: 198, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Lech Bier Dose",               category: .beer, volume: 500, abv: 5.2,  calories: 212, iconName: "cylinder.fill"),

        // Fränkische Spezialitäten
        DrinkTemplate(name: "Schlenkerla Rauchbier",        category: .beer, volume: 500, abv: 5.1,  calories: 210, iconName: "mug.fill"),
        DrinkTemplate(name: "Spezial Rauchbier",            category: .beer, volume: 500, abv: 4.6,  calories: 195, iconName: "mug.fill"),

        // Shots erweitert
        DrinkTemplate(name: "Fireball",                     category: .shot, volume: 30,  abv: 33.0, calories: 72,  iconName: "drop.fill"),
        DrinkTemplate(name: "Jägermeister Shot",            category: .shot, volume: 20,  abv: 35.0, calories: 50,  iconName: "drop.fill"),
        DrinkTemplate(name: "Mexikaner",                    category: .shot, volume: 20,  abv: 15.0, calories: 35,  iconName: "drop.fill"),
        DrinkTemplate(name: "Tequila Gold",                 category: .shot, volume: 20,  abv: 38.0, calories: 48,  iconName: "drop.fill"),
        DrinkTemplate(name: "Sambuca",                      category: .shot, volume: 25,  abv: 38.0, calories: 72,  iconName: "drop.fill"),

        // Neue Cocktails
        DrinkTemplate(name: "Dark and Stormy",              category: .cocktail, volume: 200, abv: 9.0,  calories: 185, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Spicy Margarita",              category: .cocktail, volume: 150, abv: 18.0, calories: 270, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Clover Club",                  category: .cocktail, volume: 130, abv: 18.0, calories: 185, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Spritz Veneziano",             category: .cocktail, volume: 250, abv: 8.0,  calories: 160, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Frozen Daiquiri",              category: .cocktail, volume: 150, abv: 16.0, calories: 210, iconName: "wineglass.fill"),
        DrinkTemplate(name: "Lynchburg Lemonade",           category: .cocktail, volume: 250, abv: 9.0,  calories: 200, iconName: "wineglass.fill"),

        // Alkoholfrei (Tracking-Einträge)
        DrinkTemplate(name: "Coca-Cola",                    category: .other, volume: 330, abv: 0.0, calories: 139, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Coca-Cola Zero",               category: .other, volume: 330, abv: 0.0, calories: 1,   iconName: "cylinder.fill"),
        DrinkTemplate(name: "Fanta Orange",                 category: .other, volume: 330, abv: 0.0, calories: 134, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Sprite",                       category: .other, volume: 330, abv: 0.0, calories: 105, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Red Bull",                     category: .other, volume: 250, abv: 0.0, calories: 113, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Monster Energy",               category: .other, volume: 500, abv: 0.0, calories: 228, iconName: "cylinder.fill"),
        DrinkTemplate(name: "Wasser still",                 category: .other, volume: 500, abv: 0.0, calories: 0,   iconName: "drop.fill"),
        DrinkTemplate(name: "Wasser sprudelnd",             category: .other, volume: 500, abv: 0.0, calories: 0,   iconName: "drop.fill"),
        DrinkTemplate(name: "Orangensaft",                  category: .other, volume: 200, abv: 0.0, calories: 86,  iconName: "cup.and.saucer.fill"),
        DrinkTemplate(name: "Apfelsaft",                    category: .other, volume: 200, abv: 0.0, calories: 96,  iconName: "cup.and.saucer.fill"),
    ]

    // MARK: Seeding

    @MainActor
    static func seedIfNeeded(in context: ModelContext) {
        let stored = UserDefaults.standard.integer(forKey: versionKey)
        let existing = (try? context.fetch(FetchDescriptor<DrinkTemplate>())) ?? []

        // Seed when the catalog version advanced OR the store is unexpectedly
        // empty. The empty-store case matters for sideloaded builds: the store
        // lives in the App Group container, but the version flag lives in
        // standard UserDefaults (keyed by bundle id). When a re-sign or a fresh
        // App Group container leaves an empty store while the flag still reads
        // "seeded", the version check alone would skip seeding and the drink
        // list would come up empty. Checking the actual row count self-heals it.
        guard stored < catalogVersion || existing.isEmpty else { return }

        let existingNames = Set(existing.map { $0.name })
        for template in defaults where !existingNames.contains(template.name) {
            context.insert(template)
        }
        try? context.save()
        UserDefaults.standard.set(catalogVersion, forKey: versionKey)
    }

    // MARK: Search

    @MainActor
    static func search(query: String, in context: ModelContext) throws -> [DrinkTemplate] {
        let allTemplates = try context.fetch(FetchDescriptor<DrinkTemplate>(sortBy: [SortDescriptor(\.name)]))
        if query.isEmpty {
            return allTemplates
        }
        return allTemplates.filter { $0.name.localizedStandardContains(query) }
    }

    @MainActor
    static func favourites(limit: Int = 4, in context: ModelContext) throws -> [DrinkTemplate] {
        var descriptor = FetchDescriptor<DrinkTemplate>(
            sortBy: [SortDescriptor(\.usageCount, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        return try context.fetch(descriptor)
    }
}
