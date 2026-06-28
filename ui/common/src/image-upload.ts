import { icons } from './icons';

export interface ImageUploadConfig {
  selector: string;
  inputSelector: string;
  label: string;
}

const maxSize = 8;

export function initImageUpload(config: ImageUploadConfig): void {
  const { selector, inputSelector, label } = config;

  const container = document.querySelector<HTMLElement>(selector);

  if (!container) return;

  const apiKey = container.dataset.imageSecret!;
  const url = container.dataset.imageUrl!;
  if (!apiKey || !url) return;

  const input = document.querySelector<HTMLInputElement>(inputSelector);
  if (!input) return;

  container.innerHTML = `
    <div class="image-upload">
      <div class="image-upload__dropzone" role="button" tabindex="0" aria-label="Upload cover image">
        <div class="image-upload__idle">
          <p class="image-upload__label">${label}</p>
          <p class="image-upload__hint">JPG, PNG, WebP; Max 3MB</p>
        </div>
        <div class="image-upload__progress none">
          <div class="image-upload__spinner"></div>
          <p class="image-upload__progress-label">Uploading…</p>
        </div>
        <div class="image-upload__preview none">
          <img class="image-upload__thumb" src="" alt="Cover image preview" />
          <button type="button" class="image-upload__remove button button-red" data-icon="${icons.trashBin}" aria-label="Remove image"></button>
        </div>
      </div>
      <p class="image-upload__error none"></p>
      <input class="image-upload__file-input none" type="file" accept="image/jpeg,image/png" />
    </div>
  `;

  const dropzone = container.querySelector<HTMLDivElement>('.image-upload__dropzone')!;
  const fileInput = container.querySelector<HTMLInputElement>('.image-upload__file-input')!;
  const idleView = container.querySelector<HTMLDivElement>('.image-upload__idle')!;
  const progressView = container.querySelector<HTMLDivElement>('.image-upload__progress')!;
  const previewView = container.querySelector<HTMLDivElement>('.image-upload__preview')!;
  const previewImg = container.querySelector<HTMLImageElement>('.image-upload__thumb')!;
  const removeBtn = container.querySelector<HTMLButtonElement>('.image-upload__remove')!;
  const errorEl = container.querySelector<HTMLParagraphElement>('.image-upload__error')!;

  function showState(state: 'idle' | 'progress' | 'preview'): void {
    idleView.classList.toggle('none', state !== 'idle');
    progressView.classList.toggle('none', state !== 'progress');
    previewView.classList.toggle('none', state !== 'preview');
  }

  function showError(msg: string): void {
    errorEl.textContent = msg;
    errorEl.classList.remove('none');
  }

  function clearError(): void {
    errorEl.classList.add('none');
    errorEl.textContent = '';
  }

  function reset(): void {
    input!.value = '';
    previewImg.src = '';
    fileInput.value = '';
    dropzone.classList.remove('image-upload__dropzone--over');
    clearError();
    showState('idle');
  }

  async function handleFile(file: File): Promise<void> {
    clearError();

    const allowed = ['image/jpeg', 'image/jpg', 'image/png'];
    if (!allowed.includes(file.type)) {
      showError('Only JPG and PNG images are allowed.');
      return;
    }
    if (file.size > maxSize * 1024 * 1024) {
      showError(`File size must not exceed ${maxSize}MB.`);
      return;
    }

    showState('progress');

    try {
      const response = await window.lishogi.xhr.json(
        'POST',
        `${url}/${document.body.dataset.user}`,
        {
          formData: {
            image: file,
          },
          url: {
            hash: apiKey,
          },
        },
      );

      const uuid: string = response?.uuid;
      if (!uuid) throw new Error('Server returned no UUID');

      const fullPath = `${document.body.dataset.user}/${uuid}`;

      input!.value = fullPath;

      previewImg.src = `/image-sign/${fullPath}`;
      showState('preview');
    } catch (err) {
      showState('idle');
      showError(err instanceof Error ? err.message : 'Upload failed. Please try again.');
    }
  }

  if (input.value) {
    if (input.value.startsWith('http://') || input.value.startsWith('https://'))
      previewImg.src = input.value;
    else previewImg.src = `/image-sign/${input.value}`;
    showState('preview');
  } else {
    showState('idle');
  }

  dropzone.addEventListener('click', () => fileInput.click());

  dropzone.addEventListener('keydown', e => {
    if (e.key === 'Enter' || e.key === ' ') fileInput.click();
  });

  fileInput.addEventListener('change', () => {
    const file = fileInput.files?.[0];
    if (file) handleFile(file);
  });

  dropzone.addEventListener('dragover', e => {
    e.preventDefault();
    dropzone.classList.add('image-upload__dropzone--over');
  });

  dropzone.addEventListener('dragleave', e => {
    if (!dropzone.contains(e.relatedTarget as Node)) {
      dropzone.classList.remove('image-upload__dropzone--over');
    }
  });

  dropzone.addEventListener('drop', e => {
    e.preventDefault();
    dropzone.classList.remove('image-upload__dropzone--over');
    const file = e.dataTransfer?.files[0];
    if (file) handleFile(file);
  });

  removeBtn.addEventListener('click', e => {
    e.stopPropagation();
    reset();
  });
}
