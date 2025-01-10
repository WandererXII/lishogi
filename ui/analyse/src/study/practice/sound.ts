let baseUrl: string;

function make(file: string) {
  baseUrl = baseUrl || $('body').data('asset-url') + '/assets/sound/';
  const sound = new window.Howl({
    src: [baseUrl + file + '.ogg', baseUrl + file + '.mp3'],
  });
  return () => {
    if (window.lishogi.sound.set() !== 'silent') sound.play();
  };
}

export default function (): Record<'success' | 'failure', () => void> {
  return {
    success: make('other/energy3'),
    failure: make('other/failure2'),
  };
}
