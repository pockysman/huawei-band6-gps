# Builds a retro "Car Race" LCD-game-watch style .hwt for Huawei Band 6 (HWHD07, 194x368)
# Layout positions follow model.pb (time/date/battery/weather/steps/HR/kcal slots).
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$code = @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.IO;
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Text;

public class CarRaceHwt {
    static readonly byte[] RLE = { 137, 103, 69, 35 };
    static readonly byte[] PNG_MARK = { 69, 35, 136, 136 };
    static readonly byte[] RES_PREFIX = { 85, 85, 85, 85, 1, 0, 0, 1 };

    public static Color INK = Color.FromArgb(255, 28, 36, 30);
    static Color GOLD = Color.FromArgb(255, 231, 198, 75);

    // ---------- Seven segment ----------
    static PointF[] HSeg(float xl, float xr, float yc, float t) {
        return new PointF[] { new PointF(xl, yc), new PointF(xl + t/2, yc - t/2), new PointF(xr - t/2, yc - t/2),
                              new PointF(xr, yc), new PointF(xr - t/2, yc + t/2), new PointF(xl + t/2, yc + t/2) };
    }
    static PointF[] VSeg(float xc, float yt, float yb, float t) {
        return new PointF[] { new PointF(xc, yt), new PointF(xc + t/2, yt + t/2), new PointF(xc + t/2, yb - t/2),
                              new PointF(xc, yb), new PointF(xc - t/2, yb - t/2), new PointF(xc - t/2, yt + t/2) };
    }
    static readonly string[] SEGMAP = { "abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg" };

    public static void DrawSevenSeg(Graphics g, RectangleF r, float t, int digit, Brush on, Brush ghost) {
        float x0 = r.X, y0 = r.Y, w = r.Width, h = r.Height, mid = y0 + h / 2f, gap = t * 0.18f;
        float xl = x0 + t/2 + gap, xr = x0 + w - t/2 - gap;
        var segs = new Dictionary<char, PointF[]>();
        segs['a'] = HSeg(xl, xr, y0 + t/2, t);
        segs['g'] = HSeg(xl, xr, mid, t);
        segs['d'] = HSeg(xl, xr, y0 + h - t/2, t);
        segs['f'] = VSeg(x0 + t/2, y0 + t/2 + gap, mid - gap, t);
        segs['b'] = VSeg(x0 + w - t/2, y0 + t/2 + gap, mid - gap, t);
        segs['e'] = VSeg(x0 + t/2, mid + gap, y0 + h - t/2 - gap, t);
        segs['c'] = VSeg(x0 + w - t/2, mid + gap, y0 + h - t/2 - gap, t);
        string lit = digit >= 0 ? SEGMAP[digit] : "";
        foreach (var kv in segs) {
            if (lit.IndexOf(kv.Key) >= 0) g.FillPolygon(on, kv.Value);
            else if (ghost != null) g.FillPolygon(ghost, kv.Value);
        }
    }

    static Graphics NewG(Bitmap b) {
        Graphics g = Graphics.FromImage(b);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
        g.Clear(Color.Transparent);
        return g;
    }

    public static void SaveBigDigit(string path, int d) {
        using (var bmp = new Bitmap(58, 70, PixelFormat.Format32bppArgb))
        using (var g = NewG(bmp))
        using (var on = new SolidBrush(INK))
        using (var ghost = new SolidBrush(Color.FromArgb(26, INK))) {
            g.Transform = new Matrix(1f, 0f, -0.08f, 1f, 4f, 0f); // slight LCD italic
            DrawSevenSeg(g, new RectangleF(9, 4, 40, 62), 8.5f, d, on, ghost);
            bmp.Save(path, ImageFormat.Png);
        }
    }

    public static void SaveSmallDigit(string path, int d) {
        using (var bmp = new Bitmap(8, 15, PixelFormat.Format32bppArgb))
        using (var g = NewG(bmp))
        using (var on = new SolidBrush(INK)) {
            DrawSevenSeg(g, new RectangleF(0.6f, 0.8f, 6.8f, 13.4f), 1.8f, d, on, null);
            bmp.Save(path, ImageFormat.Png);
        }
    }

    public static void SaveText(string path, string text, int w, int h, float size, Color c) {
        using (var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb))
        using (var g = NewG(bmp))
        using (var f = new Font("Arial", size, FontStyle.Bold, GraphicsUnit.Pixel))
        using (var b = new SolidBrush(c))
        using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center }) {
            g.DrawString(text, f, b, new RectangleF(0, 0, w, h), sf);
            bmp.Save(path, ImageFormat.Png);
        }
    }

    public static void SaveWeather(string path, int idx) {
        using (var bmp = new Bitmap(18, 18, PixelFormat.Format32bppArgb))
        using (var g = NewG(bmp))
        using (var p = new Pen(INK, 1.6f)) {
            if (idx <= 2) {
                g.DrawEllipse(p, 5, 5, 8, 8);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    g.DrawLine(p, 9 + (float)Math.Cos(a) * 6, 9 + (float)Math.Sin(a) * 6, 9 + (float)Math.Cos(a) * 8, 9 + (float)Math.Sin(a) * 8);
                }
            } else {
                g.DrawArc(p, 2, 6, 9, 8, 180, 180);
                g.DrawArc(p, 6, 3, 10, 10, 180, 180);
                g.DrawLine(p, 2, 12, 16, 12);
                if (idx >= 5) { g.DrawLine(p, 6, 14, 4, 17); g.DrawLine(p, 12, 14, 10, 17); }
            }
            bmp.Save(path, ImageFormat.Png);
        }
    }

    static GraphicsPath RoundRect(RectangleF r, float rad) {
        var p = new GraphicsPath();
        float d = rad * 2;
        p.AddArc(r.X, r.Y, d, d, 180, 90);
        p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    static void Txt(Graphics g, string s, float size, Color c, float x, float y, bool center = false, FontStyle st = FontStyle.Bold) {
        using (var f = new Font("Arial", size, st, GraphicsUnit.Pixel))
        using (var b = new SolidBrush(c)) {
            if (center) {
                SizeF sz = g.MeasureString(s, f);
                g.DrawString(s, f, b, x - sz.Width / 2, y);
            } else g.DrawString(s, f, b, x, y);
        }
    }

    static LinearGradientBrush Silver(RectangleF r) {
        var lg = new LinearGradientBrush(r, Color.White, Color.Gray, 90f);
        var cb = new ColorBlend();
        cb.Colors = new Color[] { Color.FromArgb(240, 242, 244), Color.FromArgb(178, 183, 188), Color.FromArgb(222, 225, 228), Color.FromArgb(150, 155, 160) };
        cb.Positions = new float[] { 0f, 0.45f, 0.7f, 1f };
        lg.InterpolationColors = cb;
        return lg;
    }

    public static void SaveBackground(string path) {
        using (var bmp = new Bitmap(194, 368, PixelFormat.Format32bppArgb))
        using (var g = Graphics.FromImage(bmp)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.Clear(Color.FromArgb(255, 10, 10, 11));

            // Silver bezel ring
            using (var sb = Silver(new RectangleF(0, 0, 194, 368)))
            using (var ring = new Pen(sb, 3f))
            using (var rp = RoundRect(new RectangleF(1.5f, 1.5f, 191, 365), 16))
                g.DrawPath(ring, rp);

            // ---------- LCD window ----------
            var lcdR = new RectangleF(5, 5, 184, 278);
            using (var lp = RoundRect(lcdR, 7)) {
                var lg = new LinearGradientBrush(lcdR, Color.White, Color.Gray, 90f);
                var cb = new ColorBlend();
                cb.Colors = new Color[] { Color.FromArgb(205, 212, 166), Color.FromArgb(196, 210, 176), Color.FromArgb(178, 204, 186) };
                cb.Positions = new float[] { 0f, 0.55f, 1f };
                lg.InterpolationColors = cb;
                g.FillPath(lg, lp);
                // reflection sheen
                using (var sheen = new SolidBrush(Color.FromArgb(22, 255, 255, 255))) {
                    g.SetClip(lp);
                    g.FillPolygon(sheen, new PointF[] { new PointF(5, 120), new PointF(120, 5), new PointF(165, 5), new PointF(5, 170) });
                    g.ResetClip();
                }
                using (var edge = new Pen(Color.FromArgb(120, 20, 26, 22), 1.5f)) g.DrawPath(edge, lp);
                lg.Dispose();
            }

            Color ink = INK;
            Color inkSoft = Color.FromArgb(150, INK);
            Color ghostC = Color.FromArgb(26, INK);
            using (var inkPen = new Pen(ink, 1.2f))
            using (var inkBrush = new SolidBrush(ink))
            using (var ghost = new SolidBrush(ghostC)) {
                // Status row: battery outline + %, degree mark
                g.DrawRectangle(inkPen, 8, 11, 14, 10);
                g.FillRectangle(inkBrush, 22, 14, 2, 4);
                Txt(g, "%", 10f, ink, 43, 10);
                using (var dp = new Pen(ink, 1f)) g.DrawEllipse(dp, 185, 9, 4, 4);
                using (var sep = new Pen(Color.FromArgb(90, INK), 1f)) g.DrawLine(sep, 9, 31, 185, 31);

                // Left LCD annotations (fixed icons)
                Txt(g, "ALM", 7.5f, ink, 10, 46);
                // bell
                g.FillPie(inkBrush, 14, 58, 12, 12, 180, 180);
                g.FillRectangle(inkBrush, 14, 63, 12, 4);
                g.FillEllipse(inkBrush, 18, 67, 4, 4);
                Txt(g, "SIG", 7.5f, inkSoft, 11, 84);
                Txt(g, "AM", 7.5f, Color.FromArgb(40, INK), 12, 128);
                Txt(g, "PM", 7.5f, ink, 12, 140);

                // Right annotations
                Txt(g, "TIME", 7.5f, ink, 156, 44);
                Txt(g, "SEC", 7.5f, ink, 155, 113);
                using (var ul = new Pen(ink, 1.5f)) g.DrawLine(ul, 154, 144, 174, 144);

                // ---------- Race track (trapezoid) ----------
                PointF[] trap = { new PointF(9, 279), new PointF(38, 186), new PointF(148, 184), new PointF(186, 279) };
                using (var tf = new SolidBrush(Color.FromArgb(150, 120, 190, 176))) g.FillPolygon(tf, trap);
                using (var tp = new Pen(ink, 2f)) g.DrawPolygon(tp, trap);

                // stepped speed scale (like the original track art)
                PointF[] scale = { new PointF(20, 274), new PointF(62, 274), new PointF(92, 200), new PointF(84, 200), new PointF(58, 266), new PointF(24, 266) };
                using (var sp = new Pen(ink, 1.6f)) g.DrawPolygon(sp, scale);
                Txt(g, "300", 7f, ink, 68, 196);
                Txt(g, "250", 7f, ink, 60, 214);
                Txt(g, "ALM", 8f, ink, 22, 226);
                Txt(g, "50 100 150", 6.5f, ink, 16, 254);

                // little race car sprite
                g.FillRectangle(inkBrush, 30, 238, 22, 7);
                g.FillRectangle(inkBrush, 36, 233, 10, 5);
                g.FillRectangle(inkBrush, 27, 240, 3, 3);
                g.FillEllipse(inkBrush, 31, 243, 6, 6);
                g.FillEllipse(inkBrush, 45, 243, 6, 6);
                using (var dash = new Pen(Color.FromArgb(110, INK), 1.4f) { DashPattern = new float[] { 3, 3 } })
                    g.DrawLine(dash, 66, 276, 98, 190);

                // stats labels (digits are right-aligned to x=170 at y=188/224/260)
                Txt(g, "STEP", 7.5f, ink, 104, 191);
                Txt(g, "PULSE", 7.5f, ink, 102, 227);
                Txt(g, "KCAL", 7.5f, ink, 104, 263);
                using (var ln = new Pen(Color.FromArgb(90, INK), 1f)) {
                    g.DrawLine(ln, 104, 214, 182, 214);
                    g.DrawLine(ln, 104, 250, 182, 250);
                }
                Txt(g, "SIG", 6.5f, ink, 40, 196);
                Txt(g, "km", 9f, ink, 38, 204, false, FontStyle.Bold | FontStyle.Italic);
                g.FillRectangle(inkBrush, 104, 276, 74, 2);
            }

            // ---------- Black body: labels, buttons, plate ----------
            Color white = Color.FromArgb(235, 238, 240);
            Txt(g, "SEC/SPEED  TIME  ALM  GAME  PM", 7.5f, white, 97, 287, true);

            // Silver ALARM band
            using (var sb2 = Silver(new RectangleF(0, 299, 194, 16)))
                g.FillPolygon(sb2, new PointF[] { new PointF(6, 300), new PointF(188, 300), new PointF(188, 314), new PointF(6, 314) });
            using (var tab = new SolidBrush(Color.FromArgb(255, 18, 18, 20)))
            using (var gp = new Pen(GOLD, 1f))
            using (var gb = new SolidBrush(GOLD)) {
                PointF[] lt = { new PointF(8, 301), new PointF(66, 301), new PointF(72, 313), new PointF(8, 313) };
                PointF[] rt = { new PointF(186, 301), new PointF(128, 301), new PointF(122, 313), new PointF(186, 313) };
                g.FillPolygon(tab, lt); g.DrawPolygon(gp, lt);
                g.FillPolygon(tab, rt); g.DrawPolygon(gp, rt);
                g.FillRectangle(gb, 11, 304, 5, 5);
                g.FillRectangle(gb, 178, 304, 5, 5);
                Txt(g, "LEFT", 7.5f, GOLD, 40, 302, true);
                Txt(g, "RIGHT", 7.5f, GOLD, 152, 302, true);
            }
            Txt(g, "ALARM", 8f, Color.FromArgb(25, 25, 28), 97, 302, true);

            // Red buttons
            foreach (float bx in new float[] { 20f, 122f }) {
                var br = new RectangleF(bx, 319, 52, 15);
                using (var bp = RoundRect(br, 5))
                using (var rb = new LinearGradientBrush(br, Color.FromArgb(240, 98, 70), Color.FromArgb(178, 46, 28), 90f))
                using (var hl = new SolidBrush(Color.FromArgb(60, 255, 255, 255))) {
                    g.FillPath(rb, bp);
                    g.FillRectangle(hl, bx + 5, 321, 42, 3);
                }
            }

            // Bottom plate
            Txt(g, "RACING", 9.5f, white, 10, 343);
            Txt(g, "CAR RACE", 9f, GOLD, 132, 344);
            var plate = new RectangleF(70, 340, 54, 18);
            using (var pp = RoundRect(plate, 4))
            using (var sb3 = Silver(plate)) g.FillPath(sb3, pp);
            Txt(g, "GD-6", 11f, Color.FromArgb(25, 25, 28), 97, 342, true);

            bmp.Save(path, ImageFormat.Png);
        }
    }

    // ---------- Binary packing (Huawei RLE BGRA) ----------
    public static byte[] EncodePng(string path) {
        using (var orig = new Bitmap(path))
        using (var bmp = new Bitmap(orig.Width, orig.Height, PixelFormat.Format32bppArgb)) {
            using (var g = Graphics.FromImage(bmp)) { g.Clear(Color.Transparent); g.DrawImage(orig, 0, 0, orig.Width, orig.Height); }
            int w = bmp.Width, h = bmp.Height;
            var data = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            byte[] raw = new byte[w * h * 4];
            Marshal.Copy(data.Scan0, raw, 0, raw.Length);
            bmp.UnlockBits(data);
            int n = w * h;
            using (var ms = new MemoryStream())
            using (var bw = new BinaryWriter(ms)) {
                bw.Write(PNG_MARK); bw.Write((ushort)w); bw.Write((ushort)h);
                int i = 0;
                while (i < n) {
                    byte b0 = raw[i*4], g0 = raw[i*4+1], r0 = raw[i*4+2], a0 = raw[i*4+3];
                    int j = i + 1;
                    while (j < n && raw[j*4] == b0 && raw[j*4+1] == g0 && raw[j*4+2] == r0 && raw[j*4+3] == a0) j++;
                    int count = j - i;
                    bool magic = b0 == RLE[0] && g0 == RLE[1] && r0 == RLE[2] && a0 == RLE[3];
                    if (count > 3 || magic) { bw.Write(RLE); bw.Write(b0); bw.Write(g0); bw.Write(r0); bw.Write(a0); bw.Write((uint)count); }
                    else for (int k = 0; k < count; k++) { bw.Write(b0); bw.Write(g0); bw.Write(r0); bw.Write(a0); }
                    i = j;
                }
                return ms.ToArray();
            }
        }
    }

    static void Put(Graphics g, string resDir, int n, int x, int y) {
        using (var im = new Bitmap(Path.Combine(resDir, string.Format("A100_{0:000}.png", n)))) g.DrawImage(im, x, y, im.Width, im.Height);
    }

    public static void RenderPreview(string resDir, string outPath) {
        using (var bg = new Bitmap(Path.Combine(resDir, "A100_002.png")))
        using (var c = new Bitmap(194, 368, PixelFormat.Format32bppArgb))
        using (var g = Graphics.FromImage(c)) {
            g.DrawImage(bg, 0, 0, 194, 368);
            // 10:58:42
            Put(g, resDir, 3 + 1, 38, 40); Put(g, resDir, 3 + 0, 92, 40);
            Put(g, resDir, 3 + 5, 38, 108); Put(g, resDir, 3 + 8, 92, 108);
            Put(g, resDir, 13 + 4, 154, 126); Put(g, resDir, 13 + 2, 164, 126);
            // battery 80, SAB 08 NOV, weather + 27
            Put(g, resDir, 13 + 8, 25, 10); Put(g, resDir, 13 + 0, 33, 10);
            Put(g, resDir, 23 + 5, 63, 10);
            Put(g, resDir, 13 + 0, 96, 10); Put(g, resDir, 13 + 8, 104, 10);
            Put(g, resDir, 30 + 10, 115, 10);
            Put(g, resDir, 42 + 1, 148, 7);
            Put(g, resDir, 13 + 2, 169, 10); Put(g, resDir, 13 + 7, 177, 10);
            Action<string, int, int> num = (s, right, y) => { int x = right - 8 * s.Length; foreach (char ch in s) { Put(g, resDir, 13 + (ch - '0'), x, y); x += 8; } };
            num("8452", 170, 188); num("76", 170, 224); num("315", 170, 260);
            c.Save(outPath, ImageFormat.Png);
        }
    }

    public static void Build(string workDir, byte[] proto, string outHwt, string previewOut) {
        string resDir = Path.Combine(workDir, "res");
        string prevDir = Path.Combine(workDir, "preview");
        Directory.CreateDirectory(resDir); Directory.CreateDirectory(prevDir);

        SaveBackground(Path.Combine(resDir, "A100_002.png"));
        for (int d = 0; d < 10; d++) {
            SaveBigDigit(Path.Combine(resDir, string.Format("A100_{0:000}.png", 3 + d)), d);
            SaveSmallDigit(Path.Combine(resDir, string.Format("A100_{0:000}.png", 13 + d)), d);
        }
        string[] wd = { "SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN" };
        for (int i = 0; i < 7; i++) SaveText(Path.Combine(resDir, string.Format("A100_{0:000}.png", 23 + i)), wd[i], 26, 15, 10f, INK);
        string[] mo = { "JAN", "FEB", "MAR", "APR", "MEI", "JUN", "JUL", "AGU", "SEP", "OKT", "NOV", "DES" };
        for (int i = 0; i < 12; i++) SaveText(Path.Combine(resDir, string.Format("A100_{0:000}.png", 30 + i)), mo[i], 26, 15, 10f, INK);
        for (int i = 0; i < 11; i++) SaveWeather(Path.Combine(resDir, string.Format("A100_{0:000}.png", 42 + i)), i);
        using (var blank = new Bitmap(8, 15, PixelFormat.Format32bppArgb)) blank.Save(Path.Combine(resDir, "A100_053.png"), ImageFormat.Png);
        using (var bmp = new Bitmap(8, 15, PixelFormat.Format32bppArgb))
        using (var g = NewG(bmp))
        using (var b = new SolidBrush(INK)) { g.FillRectangle(b, 1, 6.5f, 6, 1.8f); bmp.Save(Path.Combine(resDir, "A100_054.png"), ImageFormat.Png); }

        var enc = new List<byte[]> { new byte[] { 69, 35, 136, 136, 1, 0, 1, 0, 0, 0, 0, 0 } };
        for (int n = 2; n <= 54; n++) enc.Add(EncodePng(Path.Combine(resDir, string.Format("A100_{0:000}.png", n))));

        uint offset = (uint)RES_PREFIX.Length;
        byte[] fat, res;
        using (var fm = new MemoryStream()) using (var fw = new BinaryWriter(fm)) using (var rm = new MemoryStream()) {
            rm.Write(RES_PREFIX, 0, RES_PREFIX.Length);
            foreach (var blob in enc) { fw.Write(offset); fw.Write((uint)blob.Length); rm.Write(blob, 0, blob.Length); offset += (uint)blob.Length; }
            fat = fm.ToArray(); res = rm.ToArray();
        }
        byte[] face;
        using (var om = new MemoryStream()) using (var ow = new BinaryWriter(om)) {
            ow.Write((byte)2); ow.Write((byte)0); ow.Write((ushort)proto.Length); ow.Write((ushort)fat.Length);
            ow.Write((ushort)0); ow.Write((uint)res.Length); ow.Write((uint)0);
            ow.Write(proto); ow.Write(fat); ow.Write(res);
            face = om.ToArray();
        }

        RenderPreview(resDir, previewOut);
        string cover = Path.Combine(prevDir, "cover.jpg"), icon = Path.Combine(prevDir, "icon_small.jpg");
        using (var pv = new Bitmap(previewOut)) {
            using (var rgb = new Bitmap(194, 368, PixelFormat.Format24bppRgb)) {
                using (var g = Graphics.FromImage(rgb)) { g.Clear(Color.Black); g.DrawImage(pv, 0, 0, 194, 368); }
                rgb.Save(cover, ImageFormat.Jpeg);
                using (var sm = new Bitmap(rgb, new Size(97, 184))) sm.Save(icon, ImageFormat.Jpeg);
            }
        }

        string desc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<HwTheme><title>Car Race GD-6</title><title-cn>Car Race GD-6</title-cn>\n" +
                      "<author>Band6GPS</author><designer>Band6GPS</designer><screen>HWHD07</screen>\n" +
                      "<version>2.1.1</version><font>Default</font><font-cn>Default</font-cn></HwTheme>";
        if (File.Exists(outHwt)) File.Delete(outHwt);
        using (var fs = new FileStream(outHwt, FileMode.Create))
        using (var za = new ZipArchive(fs, ZipArchiveMode.Create)) {
            Add(za, "com.huawei.watchface", face);
            Add(za, "description.xml", new UTF8Encoding(false).GetBytes(desc));
            Add(za, "preview/cover.jpg", File.ReadAllBytes(cover));
            Add(za, "preview/icon_small.jpg", File.ReadAllBytes(icon));
        }
    }

    static void Add(ZipArchive za, string name, byte[] content) {
        var e = za.CreateEntry(name, CompressionLevel.Optimal);
        using (var s = e.Open()) s.Write(content, 0, content.Length);
    }
}
"@

Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing, System.IO.Compression, System.IO.Compression.FileSystem

$kitDir = Split-Path -Parent $PSScriptRoot
$proto = [System.IO.File]::ReadAllBytes("$kitDir\model.pb")
$work = "$env:TEMP\band6_carrace_work"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
[CarRaceHwt]::Build($work, $proto, "$kitDir\CarRace_GD6_Band6.hwt", "$kitDir\CarRace_GD6_preview.png")
Get-Item "$kitDir\CarRace_GD6_Band6.hwt", "$kitDir\CarRace_GD6_preview.png" | Select-Object Name, Length
