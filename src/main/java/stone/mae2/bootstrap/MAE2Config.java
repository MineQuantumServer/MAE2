/*
 * Copyright (C) 2024-2025 AE2 Enthusiast
 *
 * This file is part of MAE2.
 *
 * MAE2 is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * MAE2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */
package stone.mae2.bootstrap;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.neoforged.fml.event.config.ModConfigEvent;

import stone.mae2.MAE2;
import stone.mae2.bootstrap.MAE2Config.TickRates.TickRate;
import stone.mae2.util.TransHelper;

@EventBusSubscriber(modid = MAE2.MODID, bus = EventBusSubscriber.Bus.MOD)
public record MAE2Config(Client client, Parts parts, Faulty faulty) {

  @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
    onReload();
  }

  // load in things that don't require a restart here (ie client only things or
  // behavioral things)
  public static void onReload() {
    // this record thing sounded neat but idk now
    // just make the entire config anew here, this a once in a while thing
    // really
    MAE2.CONFIG =
      new MAE2Config
      (new Client
       (read(CLOUD_CHAMBER_FACTOR, CLIENT)),
       new Parts
       (read(EU_P2P, COMMON),
        read(EU_P2P_NERF, COMMON),
        read(EU_P2P_NERF_FACTOR, COMMON),
        new TickRates
        (new TickRate(read(FE_MIN_RATE, COMMON), read(FE_MAX_RATE, COMMON)),
         new TickRate(read(EU_MIN_RATE, COMMON), read(EU_MAX_RATE, COMMON)),
         new TickRate(read(PATTERN_MIN_RATE, COMMON), read(PATTERN_MAX_RATE, COMMON)))),
       new Faulty(read(UBER_MAX_VOLUME, COMMON)));
  }

  // load in things that require a restart here (ie item registation or p2p
  // attunements)
  public static void onLoad() {
    onReload();
  }

  @SubscribeEvent
    static void onload(final ModConfigEvent.Loading event) {
    onLoad();
  }

  private static <T> T read(ModConfigSpec.ConfigValue<T> value, ModConfigSpec spec) {
    return spec.isLoaded() ? value.get() : value.getDefault();
  }

  public record Client(double cloudChamberFactor) {}

  public record Parts(boolean isEUP2PEnabled, boolean isEUP2PNerfed,
                      double EUP2PNerfFactor, TickRates rates) {}

  public record TickRates(TickRate FEMultiP2PTunnel, TickRate EUMultiP2PTunnel,
                          TickRate PatternP2PTunnel) {
    public record TickRate(int minRate, int maxRate) {}
  }

  public record Faulty(int maxUberVolume) {}

  // client
  private static final ModConfigSpec.DoubleValue CLOUD_CHAMBER_FACTOR;

  // parts
  private static final ModConfigSpec.BooleanValue EU_P2P;
  private static final ModConfigSpec.BooleanValue EU_P2P_NERF;
  private static final ModConfigSpec.DoubleValue EU_P2P_NERF_FACTOR;

  // tick rates
  private static final ModConfigSpec.IntValue FE_MIN_RATE;
  private static final ModConfigSpec.IntValue FE_MAX_RATE;

  private static final ModConfigSpec.IntValue EU_MIN_RATE;
  private static final ModConfigSpec.IntValue EU_MAX_RATE;

  private static final ModConfigSpec.IntValue PATTERN_MIN_RATE;
  private static final ModConfigSpec.IntValue PATTERN_MAX_RATE;

  // faulty card modes
  private static final ModConfigSpec.IntValue UBER_MAX_VOLUME;

  public static final ModConfigSpec CLIENT;
  public static final ModConfigSpec COMMON;

  static {
    ModConfigSpec.Builder client = new ModConfigSpec.Builder();
    ModConfigSpec.Builder common = new ModConfigSpec.Builder();

    client.push("Visual");
    CLOUD_CHAMBER_FACTOR = client
      .comment("Global modifier to amount of particles spawned in cloud chambers (Semi-Vibrant Glass)")
      .translation(TransHelper.CONFIG.toKey("cloudChamberFactor"))
      .defineInRange("cloudChamberFactor", 1, 0, Double.MAX_VALUE);
    client.pop();
    
    // parts
    common.push("Parts");
    common.push("EU P2P");
    EU_P2P = common
      .comment("Whether the EU P2P (single or multi) is enabled. !!Requires Game Restart!!")
      .worldRestart()
      .translation(TransHelper.CONFIG.toKey("euP2P"))
      .define("enabled", true);

    EU_P2P_NERF = common
      .comment(
               "Enable/Disable nerf to EU p2p. Nerf penalizes higher energy transfer rates across the entire ME network with higher taxes, but in such a way that stepping up voltage reduces tax. Also prevents EU p2ps from getting channels though an ME p2p")
      .translation(TransHelper.CONFIG.toKey("euP2PNerf"))
      .define("nerf", false);

    EU_P2P_NERF_FACTOR = common
      .comment(
               "A factor used in calculating the nerfed EU p2p's tax. Higher means the tax is higher. Linearly affects tax")
      .worldRestart()
      .translation(TransHelper.CONFIG.toKey("euP2PNerfFactor"))
      .defineInRange("nerfFactor", 0.05, 0, Double.MAX_VALUE);

    common.pop();

    // tick rates
    common.push("FE Multi P2P");
    FE_MIN_RATE = common
      .comment("Min tick rate for FE Multi P2P Tunnels")
      .translation(TransHelper.CONFIG.toKey("feMinRate"))
      .defineInRange("minRate", 1, 1, Integer.MAX_VALUE);
    FE_MAX_RATE = common
      .comment("Max tick rate for FE Multi P2P Tunnels")
      .translation(TransHelper.CONFIG.toKey("feMaxRate"))
      .defineInRange("maxRate", 1, 1, Integer.MAX_VALUE);
    common.pop();

    common.push("EU Multi P2P");
    EU_MIN_RATE = common
      .comment("Min tick rate for EU Multi P2P Tunnels")
      .translation(TransHelper.CONFIG.toKey("euMinRate"))
      .defineInRange("minRate", 1, 1, Integer.MAX_VALUE);
    EU_MAX_RATE = common
      .comment("Max tick rate for EU Multi P2P Tunnels")
      .translation(TransHelper.CONFIG.toKey("euMaxRate"))
      .defineInRange("maxRate", 1, 1, Integer.MAX_VALUE);
    common.pop();

    common.push("Pattern P2P");
    PATTERN_MIN_RATE = common
      .comment("Minimum tick rate for Pattern (Multi) P2P Tunnels")
      .translation(TransHelper.CONFIG.toKey("patternMinRate"))
      .defineInRange("minRate", 5, 1, Integer.MAX_VALUE);
    PATTERN_MAX_RATE = common
      .comment("Max tick rate for Pattern (Multi) P2P Tunnels")
      .translation(TransHelper.CONFIG.toKey("patternMaxRate"))
      .defineInRange("maxRate", 120, 1, Integer.MAX_VALUE);
    common.pop();
    common.pop();

    common.push("Faulty Card Modes");
    UBER_MAX_VOLUME = common
      .comment("Max volume in blocks for uber mode of the faulty card")
      .translation(TransHelper.CONFIG.toKey("uberMaxVolume"))
      .defineInRange("uberMaxVolume", 32 * 32 * 32, 0, Integer.MAX_VALUE);

    CLIENT = client.build();
    COMMON = common.build();
  }
}
