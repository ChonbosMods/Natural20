package com.chonbosmods.dice.render;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.Random;

/**
 * Build-time tool: renders d20 sprite sheets for in-game UI.
 * Outputs 10 tumble frames + 20 result frames as 128x128 PNGs.
 *
 * Run: java -cp build/classes/java/main com.chonbosmods.dice.render.DiceSpriteGenerator [outputDir]
 */
public class DiceSpriteGenerator {

    public static final int TUMBLE_FRAME_COUNT = 10;
    public static final String TUMBLE_PREFIX = "d20_tumble_";
    public static final String RESULT_PREFIX = "d20_result_";

    public static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0]
                : "src/main/resources/Common/UI/Custom/Textures/Dice";
        generate(outputDir);
    }

    public static void generate(String outputDir) throws Exception {
        File dir = new File(outputDir);
        dir.mkdirs();

        DiceRenderer renderer = new DiceRenderer();
        Random rng = new Random(42); // fixed seed for reproducible builds

        // --- Tumble frames: 10 chaotic orientations ---
        for (int i = 1; i <= TUMBLE_FRAME_COUNT; i++) {
            float[] axis = Nat20Math.normalize(new float[]{
                rng.nextFloat() - 0.5f,
                rng.nextFloat() - 0.5f,
                rng.nextFloat() - 0.5f
            });
            float angle = rng.nextFloat() * (float)(2 * Math.PI);
            float[] quat = Nat20Math.quaternionFromAxisAngle(axis, angle);

            renderer.render(quat);
            savePng(renderer.getPixels(), new File(dir, String.format("%s%02d.png", TUMBLE_PREFIX, i)));
        }

        // --- Result frames: one per face 1-20 ---
        for (int result = 1; result <= 20; result++) {
            float[] quat = DiceFaceLookup.RESULT_ROTATIONS[result];
            renderer.render(quat);
            savePng(renderer.getPixels(), new File(dir, String.format("%s%02d.png", RESULT_PREFIX, result)));
        }

        System.out.printf("Generated %d tumble + 20 result = %d PNGs in %s%n",
                TUMBLE_FRAME_COUNT, TUMBLE_FRAME_COUNT + 20, dir.getAbsolutePath());
    }

    private static void savePng(int[] pixels, File file) throws Exception {
        BufferedImage img = new BufferedImage(DiceRenderer.WIDTH, DiceRenderer.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, DiceRenderer.WIDTH, DiceRenderer.HEIGHT, pixels, 0, DiceRenderer.WIDTH);
        ImageIO.write(img, "PNG", file);
    }
}
