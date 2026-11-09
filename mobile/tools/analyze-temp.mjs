import sharp from 'sharp';
import path from 'node:path';

const base = process.env.EOC_ASSETS_DIR || path.resolve('assets-fuente');

async function analyze(file, label) {
  const { data, info } = await sharp(path.join(base, file))
    .removeAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  const { width, height, channels } = info;
  let minX = width, minY = height, maxX = -1, maxY = -1;
  let count = 0;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * channels;
      const r = data[i], g = data[i + 1], b = data[i + 2];
      const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
      if (lum > 235 && r > 235 && g > 235 && b > 235) {
        count++;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  const px = (x, y) => {
    const i = (y * width + x) * channels;
    return '#' + [data[i], data[i + 1], data[i + 2]].map(v => v.toString(16).padStart(2, '0')).join('');
  };
  console.log(`== ${label} ==`);
  console.log(`size ${width}x${height} channels ${channels} ratio ${(width / height).toFixed(4)}`);
  console.log(`white bbox x ${minX}..${maxX} (${maxX - minX + 1}px, ${(((maxX - minX + 1) / width) * 100).toFixed(2)}%) y ${minY}..${maxY} (${maxY - minY + 1}px, ${(((maxY - minY + 1) / height) * 100).toFixed(2)}%) pixels ${count}`);
  console.log(`center ${px(width >> 1, height >> 1)} corners TL ${px(0, 0)} TR ${px(width - 1, 0)} BL ${px(0, height - 1)} BR ${px(width - 1, height - 1)}`);
  console.log(`edges mid: top ${px(width >> 1, 0)} bottom ${px(width >> 1, height - 1)} left ${px(0, height >> 1)} right ${px(width - 1, height >> 1)}`);
  // average color of the non-white background
  let sr = 0, sg = 0, sb = 0, n = 0;
  for (let y = 0; y < height; y += 3) {
    for (let x = 0; x < width; x += 3) {
      const i = (y * width + x) * channels;
      const lum = 0.2126 * data[i] + 0.7152 * data[i + 1] + 0.0722 * data[i + 2];
      if (lum < 200) { sr += data[i]; sg += data[i + 1]; sb += data[i + 2]; n++; }
    }
  }
  console.log(`avg bg #${[sr / n, sg / n, sb / n].map(v => Math.round(v).toString(16).padStart(2, '0')).join('')} (${n} samples)`);
}

await analyze('logo.jpg', 'logo');
await analyze('pantalladecarga.jpg', 'splash');
