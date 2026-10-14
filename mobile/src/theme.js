import { Platform } from "react-native";

export const colors = {
  night: "#001f3a",
  raised: "#0a2a47",
  field: "#001a31",
  text: "#f4f6ed",
  textMuted: "#b9d3dc",
  peach: "#fca324",
  sky: "#67bed9",
  line: "rgba(244, 246, 237, 0.10)",
  lineStrong: "rgba(244, 246, 237, 0.24)",
  shadowInk: "#000c18",
  white: "#ffffff",
  scrim: "rgba(0, 12, 24, 0.72)",
  ok: "#3ccf7a",
  repeat: "#ffc83d",
  stop: "#e5484d",
  // The refusal colour for text and icons on the navy background, where `stop` is too dim.
  stopSoft: "#ff8a8a",
  notSaved: "#f4f6ed",
  clear: "transparent",
};

// Colour belongs to results only; the rest of the app stays navy.
export const results = {
  ok: { background: colors.ok, ink: colors.night, accent: colors.night },
  repeat: { background: colors.repeat, ink: colors.night, accent: colors.night },
  stop: { background: colors.stop, ink: colors.white, accent: colors.white },
  sponsor: { background: colors.sky, ink: colors.night, accent: colors.night },
  void: { background: colors.notSaved, ink: colors.night, accent: colors.stop },
  calm: { background: colors.raised, ink: colors.text, accent: colors.textMuted },
};

// Fonts are embedded at build time by the expo-font plugin. Android names a font
// after its file, iOS after the name inside the file. Each is one weight, so
// `fontWeight` is never set alongside them.
const family = (android, ios) => Platform.select({ android, default: ios });

export const fonts = {
  regular: family("OpenSans_400Regular", "OpenSans-Regular"),
  semi: family("OpenSans_600SemiBold", "OpenSans-SemiBold"),
  bold: family("OpenSans_700Bold", "OpenSans-Bold"),
  extra: family("OpenSans_800ExtraBold", "OpenSans-ExtraBold"),
  brand: family("Agbalumo_400Regular", "Agbalumo-Regular"),
};

export const type = {
  wordmark: { fontFamily: fonts.brand, fontSize: 22, lineHeight: 28 },
  name: { fontFamily: fonts.bold, fontSize: 34, lineHeight: 40 },
  title: { fontFamily: fonts.bold, fontSize: 28, lineHeight: 34 },
  heading: { fontFamily: fonts.bold, fontSize: 22, lineHeight: 28 },
  row: { fontFamily: fonts.semi, fontSize: 17, lineHeight: 22 },
  body: { fontFamily: fonts.regular, fontSize: 17, lineHeight: 24 },
  secondary: { fontFamily: fonts.regular, fontSize: 15, lineHeight: 20 },
  caption: { fontFamily: fonts.semi, fontSize: 13, lineHeight: 18 },
  button: { fontFamily: fonts.semi, fontSize: 17, lineHeight: 22 },
};

export const space = { xs: 4, sm: 8, md: 12, lg: 16, gutter: 20, xl: 24, xxl: 32, huge: 48 };

export const radii = { button: 14, panel: 20, pill: 999 };

export const sizes = { primary: 56, row: 56, rowTall: 64, text: 44, field: 52, header: 56 };
