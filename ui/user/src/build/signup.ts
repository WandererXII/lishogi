import { spinnerHtml } from 'common/spinner';
import { debounce } from 'common/timings';

// see FormTimer.scala
const minFormAge = 3000;

window.lishogi.ready.then(() => {
  const readyAt = performance.now() + minFormAge;
  const $form = $('#signup_form');
  const $exists = $form.find('.username-exists');
  const $username = $form
    .find<HTMLInputElement>('input[name="username"]')
    .on('change keyup paste', () => {
      $exists.hide();
      usernameCheck();
    });

  const usernameCheck = debounce(() => {
    const name = $username.val();
    if (name && name.length >= 3)
      window.lishogi.xhr
        .json('GET', '/api/player/autocomplete', {
          url: { term: name, exists: true },
        })
        .then(res => {
          $exists.toggle(res);
        });
  }, 300);

  let submitting = false;
  $form.on('submit', e => {
    if (submitting) {
      e.preventDefault();
      return;
    }
    submitting = true;

    $form
      .find('button.submit')
      .prop('disabled', true)
      .removeAttr('data-icon')
      .addClass('frameless')
      .html(spinnerHtml);

    const wait = readyAt - performance.now();
    if (wait > 0) {
      e.preventDefault();
      setTimeout(() => ($form[0] as HTMLFormElement).submit(), wait);
    }
  });

  // back button: the restored page would still be locked
  window.addEventListener('pageshow', e => {
    if (e.persisted) window.location.reload();
  });
});
