import { createIconSelector, svgSprite } from 'common/icon-selector';

window.lishogi.ready.then(() => {
  function updateFormatClass() {
    const $select = $('#form3-format');
    const $form = $('.form3');
    const selectedValue = $select.val();

    $form.removeClass('f-arena f-robin f-organized');

    $form.addClass(`f-${selectedValue}`);
  }
  function updateTimeControlClass() {
    const $select = $('#form3-timeControlSetup_timeControl');
    const $form = $('.form3');
    const selectedValue = $select.val();

    $form.removeClass('f-corres f-rt');
    $form.addClass(`f-${selectedValue == 1 ? 'rt' : 'corres'}`);
  }

  updateFormatClass();
  updateTimeControlClass();

  $('#form3-format').on('change', updateFormatClass);
  $('#form3-timeControlSetup_timeControl').on('change', updateTimeControlClass);

  const selectorInputEl = document.getElementById('form3-icon') as HTMLInputElement;
  const selectorEl = document.getElementById('icon-selector')!;
  selectorEl.innerHTML = '<div class="icon"></div>';
  function updateIconSelectorDisplay(icon: string | undefined) {
    const selectorIcon = selectorEl.getElementsByClassName('icon')[0];
    selectorIcon.innerHTML = icon ? svgSprite('tour', icon) : '';
  }
  updateIconSelectorDisplay(selectorInputEl.value);
  let selector: any;
  selectorEl.addEventListener('click', () => {
    if (selector) selector.toggle();
    else
      selector = createIconSelector(selectorEl, selectorInputEl.value || '', 'tour', key => {
        selectorInputEl.value = key;
        updateIconSelectorDisplay(key);
      });
  });

  $('.form-fieldset--toggle').each(function () {
    const toggle = () => this.classList.toggle('form-fieldset--toggle-off');
    $(this)
      .children('legend')
      .on('click', toggle)
      .on('keypress', e => e.key == 'Enter' && toggle());
  });

  document
    .querySelectorAll<HTMLInputElement>('main form .flatpickr')
    .forEach((el: HTMLInputElement) => {
      const fp = window.flatpickr(el, {
        minDate: !el.value ? new Date(Date.now() + 1000 * 60) : undefined,
        maxDate: new Date(Date.now() + 1000 * 3600 * 24 * 31 * 3),
        dateFormat: 'Z',
        altInput: true,
        altFormat: 'Y-m-d h:i K',
        enableTime: true,
        time_24hr: true,
        disableMobile: true,
        locale: document.documentElement.lang as any,
      });
      if (el.classList.contains('flatpickr--disabled')) fp._input.disabled = true;
    });
});
