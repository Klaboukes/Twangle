import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Shrinks one picture for the website: ShrinkImage <in.png> <out.jpg|out.png> <width> [trim].
 * A .jpg is written at quality 0.85 without alpha (screenshots); a .png keeps its alpha (the icon,
 * the Play badge). "trim" first cuts away fully transparent rows and columns around the picture.
 * Run with Android Studio's JDK, no build needed: java website/tools/ShrinkImage.java ...
 */
public class ShrinkImage {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: ShrinkImage <in> <out.jpg|out.png> <width> [trim]");
            System.exit(2);
        }
        BufferedImage in = ImageIO.read(new File(args[0]));
        if (in == null) throw new IllegalArgumentException("not a picture: " + args[0]);
        if (args.length > 3 && args[3].equals("trim")) in = trim(in);
        int width = Integer.parseInt(args[2]);
        int height = Math.round((float) in.getHeight() * width / in.getWidth());
        boolean jpeg = args[1].toLowerCase().endsWith(".jpg");
        BufferedImage out = new BufferedImage(width, height,
                jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        // Area averaging is slow but gives the cleanest result when shrinking a lot.
        Image scaled = in.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
        Graphics2D g = out.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        File file = new File(args[1]);
        file.getAbsoluteFile().getParentFile().mkdirs();
        if (jpeg) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            file.delete();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(file)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(out, null, null), param);
            }
            writer.dispose();
        } else {
            ImageIO.write(out, "png", file);
        }
        System.out.println(args[1] + ": " + width + " x " + height + ", " + file.length() / 1024 + " KB");
    }

    private static BufferedImage trim(BufferedImage in) {
        int left = in.getWidth(), right = -1, top = in.getHeight(), bottom = -1;
        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                if ((in.getRGB(x, y) >>> 24) != 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) return in;
        return in.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }
}
