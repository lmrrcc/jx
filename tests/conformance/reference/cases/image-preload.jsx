import {useState} from 'react';
import {preload} from 'react-dom';

const sources = '/__bench/images/small.svg 40w, /__bench/images/large.svg 80w';
const responsive = imageSizes => ({as: 'image', crossOrigin: 'anonymous', fetchPriority: 'high',
  type: 'image/svg+xml', imageSrcSet: sources, imageSizes, referrerPolicy: 'no-referrer'});

export function ImagePreload() {
  const [repeated, setRepeated] = useState(0);
  const [wide, setWide] = useState(false);
  preload('/__bench/images/fallback.svg', responsive('30px'));
  preload('/__bench/images/other-fallback.svg', responsive('30px'));
  preload('/__bench/images/legacy.svg', {as: 'image', fetchPriority: 'low', type: 'image/svg+xml'});
  return <main>
    <output data-testid="image-status">{(wide ? 'wide:' : 'narrow:') + repeated}</output>
    <button onClick={() => {
      preload('/__bench/images/repeated-fallback.svg', responsive('30px'));
      setRepeated(value => value + 1);
    }}>Repeat responsive preload</button>
    <button onClick={() => {
      preload('/__bench/images/fallback.svg', responsive('70px'));
      setWide(true);
    }}>Preload wide image</button>
  </main>;
}
