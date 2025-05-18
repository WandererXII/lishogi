const selector = '.auth-login form';

window.lishogi.ready.then(() => {
  load($(selector));
});

function load($f: JQuery): void {
  $f.on('submit', () => {
    $f.find('.submit').prop('disabled', true);

    window.lishogi.xhr
      .formToXhr($f[0] as HTMLFormElement)
      .then(res => res.text())
      .then(res => {
        if (res === 'MissingTotpToken' || res === 'InvalidTotpToken') {
          $f.find('.one-factor').hide();
          $f.find('.two-factor').show();
          requestAnimationFrame(() => {
            $f.find('.two-factor input').val('').trigger('focus');
          });
          $f.find('.submit').prop('disabled', false);
          if (res === 'InvalidTotpToken') $f.find('.two-factor .error').show();
        } else location.href = res.startsWith('ok:') ? res.slice(3) : '/';
      })
      .catch(error => {
        try {
          const res = error as Response;
          res.text().then(responseText => {
            try {
              const el = $(responseText).find(selector);
              if (el.length) {
                $f.replaceWith(el);
                load($(selector));
              } else {
                alert(
                  responseText || `${res.statusText}. Please wait some time before trying again.`,
                );
                $f.find('.submit').prop('disabled', false);
              }
            } catch {
              $f.html(responseText);
            }
          });
        } catch {
          alert(`Error occured - ${error}`);
        }
      });
    return false;
  });
}
