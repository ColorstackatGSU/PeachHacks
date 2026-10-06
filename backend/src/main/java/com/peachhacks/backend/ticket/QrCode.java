package com.peachhacks.backend.ticket;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

/**
 * Writes the PNG by hand (1-bit greyscale) instead of through ImageIO so rendering needs no
 * AWT, which is not guaranteed to be usable in a slim server image.
 */
final class QrCode {

	/** The QR specification asks for four modules of white around the symbol. */
	private static final int QUIET_ZONE = 4;

	private static final int TARGET_PIXELS = 640;

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private QrCode() {
	}

	static byte[] png(String content) {
		ByteMatrix modules;
		try {
			modules = Encoder.encode(content, ErrorCorrectionLevel.M).getMatrix();
		}
		catch (WriterException ex) {
			throw new IllegalStateException("Could not encode the QR code", ex);
		}
		int count = modules.getWidth() + 2 * QUIET_ZONE;
		// A whole number of pixels per module keeps every edge sharp when a phone scales the image.
		int scale = Math.max(4, TARGET_PIXELS / count);
		int size = count * scale;
		int rowBytes = (size + 7) / 8;

		try {
			ByteArrayOutputStream pixels = new ByteArrayOutputStream();
			try (DeflaterOutputStream deflater = new DeflaterOutputStream(pixels)) {
				byte[] row = new byte[1 + rowBytes];
				for (int y = 0; y < size; y++) {
					Arrays.fill(row, (byte) 0);
					int moduleY = y / scale - QUIET_ZONE;
					for (int x = 0; x < size; x++) {
						int moduleX = x / scale - QUIET_ZONE;
						boolean dark = moduleX >= 0 && moduleY >= 0 && moduleX < modules.getWidth()
								&& moduleY < modules.getHeight() && modules.get(moduleX, moduleY) == 1;
						if (!dark) {
							row[1 + x / 8] |= (byte) (0x80 >> (x % 8));
						}
					}
					deflater.write(row);
				}
			}

			ByteArrayOutputStream header = new ByteArrayOutputStream();
			DataOutputStream ihdr = new DataOutputStream(header);
			ihdr.writeInt(size);
			ihdr.writeInt(size);
			ihdr.write(new byte[] { 1, 0, 0, 0, 0 });

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			out.write(PNG_SIGNATURE);
			chunk(out, "IHDR", header.toByteArray());
			chunk(out, "IDAT", pixels.toByteArray());
			chunk(out, "IEND", new byte[0]);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
		byte[] name = type.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(name);
		crc.update(data);
		DataOutputStream stream = new DataOutputStream(out);
		stream.writeInt(data.length);
		stream.write(name);
		stream.write(data);
		stream.writeInt((int) crc.getValue());
	}

}
