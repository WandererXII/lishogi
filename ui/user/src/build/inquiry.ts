window.lishogi.ready.then(() => {
  $('#inquiry .notes').on('mouseenter', function () {
    ($(this).find('textarea')[0] as HTMLTextAreaElement).focus();
  });
  $('#inquiry .costello').on('click', () => {
    $('#inquiry').toggleClass('hidden');
    $('body').toggleClass('no-inquiry');
  });

  const nextStore = window.lishogi.storage.makeBoolean('inquiry-auto-next');

  if (!nextStore.get()) {
    $('#inquiry .switcher input').prop('checked', false);
    $('#inquiry input.auto-next').val('0');
  }

  $('#inquiry .switcher input').on('change', function (this: HTMLInputElement) {
    nextStore.set(this.checked);
    $('#inquiry input.auto-next').val(this.checked ? '1' : '0');
  });
});
