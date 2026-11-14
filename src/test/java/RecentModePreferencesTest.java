package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;

class RecentModePreferencesTest {
 @BeforeAll static void init(){Config.setPreferencesInstance(new MemoryPreferences());}
 @Test void validOptionsRoundTripWithoutPersistingApiKeys(){var prefs=Config.getPref();var r=new SourceRegistry();var source=r.add("Private");r.setEndpoint("https://example.test",false);r.setKey(source.id,"dummy-key-never-persist");var opts=new LoadOptions(LoadOptions.Mode.MOST_RECENT,321,654);ConnectionPreferences.save(prefs,r,LocalDate.of(2024,1,1),LocalDate.of(2024,2,1),opts);assertEquals(opts,ConnectionPreferences.load(prefs).options());assertFalse(prefs.toString().contains("dummy-key-never-persist"));assertNull(ConnectionPreferences.load(prefs).registry().session(source.id).snapshot().apiKey());}
 @Test void corruptOptionsFallBackToDefaults(){var prefs=Config.getPref();prefs.put("dawarich.loadMode","NOPE");prefs.putInt("dawarich.recentPointsPerSource",0);prefs.putInt("dawarich.totalPointLimit",999999);assertEquals(LoadOptions.defaults(),ConnectionPreferences.load(prefs).options());}
}
