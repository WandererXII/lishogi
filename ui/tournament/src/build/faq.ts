window.lishogi.ready.then(() => {
  const allButtons = document.querySelectorAll<HTMLElement>('.format-button');
  const allFaq = document.querySelectorAll('.body > div');
  allButtons.forEach((el: HTMLElement) => {
    el.addEventListener('click', () => {
      allButtons.forEach(el => {
        el.classList.remove('selected');
      });
      el.classList.add('selected');

      const key = el.dataset.key;
      allFaq.forEach(faqEl => {
        faqEl.classList.add('none');
      });
      document.querySelector(`.body > .${key}`)!.classList.remove('none');

      const url = new URL(window.location.href);
      url.searchParams.set('format', key || '');
      history.pushState(null, '', url.toString());
    });
  });
});
