import { icons } from 'common/icons';
import { debounce } from 'common/timings';

const maxCategs = 3;

window.lishogi.ready.then(() => {
  const match = window.location.pathname.match(/^\/article\/([^/]+)\//);
  const id = match?.[1];
  if (!id) throw new Error('no id');

  const chipList = document.querySelector<HTMLElement>('.chip-list');

  if (chipList) {
    const chips = Array.from(chipList.querySelectorAll<HTMLElement>('.chip'));
    const selected = new Set<string>(
      chips.filter(chip => chip.classList.contains('selected')).map(chip => chip.dataset.categ!),
    );

    function updateFullState() {
      if (selected.size >= 3) chipList!.classList.add('full');
      else chipList!.classList.remove('full');
    }
    updateFullState();

    function sync() {
      const categs = [...selected].join(',');
      window.lishogi.xhr.text('POST', `/article/categories/${id}`, {
        url: {
          categs: categs,
        },
      });
    }

    const update = debounce(sync, 1000);

    chips.forEach(chip => {
      chip.addEventListener('click', () => {
        const c = chip.dataset.categ!;
        const on = selected.has(c);

        if (!on && selected.size >= maxCategs) {
          window.lishogi.announce({
            tpe: 'failure',
            msg: `Max ${maxCategs} categories can be set`,
          });
          return;
        }

        chip.classList.toggle('selected');
        on ? selected.delete(c) : selected.add(c);

        updateFullState();
        update();
      });
    });
  }

  document.querySelectorAll<HTMLElement>('.like-button').forEach(btn => {
    btn.addEventListener('click', async () => {
      const likedBefore = btn.dataset.liked === 'true';
      const likedAfter = !likedBefore;

      console.log(likedBefore, likedAfter);

      const res = await window.lishogi.xhr.text('POST', `/article/like/${id}`, {
        url: { v: String(likedAfter) },
      });

      document.querySelectorAll<HTMLElement>('.like-button').forEach(b => {
        b.dataset.liked = String(likedAfter);
        b.textContent = res;
        b.dataset.icon = likedAfter ? icons.heartFull : icons.heartOutline;
      });
    });
  });
});
