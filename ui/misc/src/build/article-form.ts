import Editor from '@toast-ui/editor';
import { initImageUpload } from 'common/image-upload';
import { i18n } from 'i18n';

function initEditor(selector: string, textareaSelector: string) {
  const el = document.querySelector<HTMLElement>(selector)!;
  const textarea = document.querySelector<HTMLTextAreaElement>(textareaSelector)!;

  const apiKey = el.dataset.imageSecret!;
  const url = el.dataset.imageUrl!;

  const editor = new Editor({
    el,
    height: '500px',
    initialEditType: 'wysiwyg',
    hideModeSwitch: !el.dataset.mod,
    usageStatistics: false,
    initialValue: textarea.value || '',
    autofocus: false,
    toolbarItems: [
      ['heading', 'bold', 'italic', 'strike'],
      ['hr', 'quote'],
      ['ul', 'ol'],
      ['table', 'image', 'link'], // Added 'image' to the toolbar
      ['code', 'codeblock'],
    ],
    hooks: {
      addImageBlobHook: async (
        blob: Blob | File,
        callback: (url: string, altText?: string) => void,
      ) => {
        // Enforce the same size limit (8MB) as your standalone uploader
        const maxSize = 8;
        if (blob.size > maxSize * 1024 * 1024) {
          alert(`File size must not exceed ${maxSize}MB.`);
          return;
        }

        // Enforce the same file type restrictions
        const allowed = ['image/jpeg', 'image/jpg', 'image/png'];
        if (!allowed.includes(blob.type)) {
          alert('Only JPG and PNG images are allowed.');
          return;
        }

        try {
          if (!apiKey || !url) {
            throw new Error('Upload configuration missing on editor element.');
          }

          // Use your custom window.lishogi XHR wrapper exactly like your other component
          const response = await window.lishogi.xhr.json(
            'POST',
            `${url}/${document.body.dataset.user}`,
            {
              formData: {
                image: blob,
              },
              url: {
                hash: apiKey,
              },
            },
          );

          const uuid: string = response?.uuid;
          if (!uuid) throw new Error('Server returned no UUID');

          const signedUrl = await window.lishogi.xhr
            .json('GET', `/image-sign/${document.body.dataset.user}/${uuid}`)
            .then(json => json.url);
          const altText = blob instanceof File ? blob.name : 'image';

          callback(signedUrl, altText);
        } catch (err) {
          console.error('Image upload failed:', err);
          alert(err instanceof Error ? err.message : 'Upload failed. Please try again.');
        }
      },
    },
  });

  const form = textarea.closest('form')!;
  form.addEventListener('submit', () => {
    textarea.value = editor.getMarkdown();
  });

  return editor;
}

window.lishogi.ready.then(() => {
  initEditor('#markdown-editor', '#form3-body');

  initImageUpload({
    selector: '#image-editor',
    inputSelector: '#form3-image',
    label: i18n('chooseFileOrDrag'),
  });
});
