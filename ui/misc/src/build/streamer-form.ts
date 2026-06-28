import { initImageUpload } from 'common/image-upload';
import { i18n } from 'i18n';

window.lishogi.ready.then(() => {
  initImageUpload({
    selector: '#image-editor',
    inputSelector: '#form3-picturePath',
    label: i18n('chooseFileOrDrag'),
  });
});
