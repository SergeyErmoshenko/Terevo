import type { Action } from "../../app/actions";
import type { MediaViewerDto } from "../../app/dto";
import { fileUrl } from "../../app/client";
import { Icon } from "../icons/Icon";
import { Dialog } from "./Dialog";

export function MediaViewerDialog({ viewer, dispatch }: { viewer: MediaViewerDto; dispatch: (action: Action) => void }) {
  const isPdf = viewer.media.mimeType === "application/pdf";
  const url = fileUrl(viewer.imagePath);

  return (
    <Dialog
      eyebrow="ПРОСМОТР ФАЙЛА"
      title={viewer.media.fileName}
      onClose={() => dispatch({ type: "closeMedia" })}
      width="lg"
      footer={
        <>
          {isPdf && viewer.pageCount > 1 && (
            <div className="media-pager">
              <button className="icon-button" type="button" disabled={viewer.page === 0} onClick={() => dispatch({ type: "changeMediaPage", delta: -1 })}>
                <Icon name="chevronLeft" />
              </button>
              <span>
                {viewer.page + 1} / {viewer.pageCount}
              </span>
              <button className="icon-button" type="button" disabled={viewer.page >= viewer.pageCount - 1} onClick={() => dispatch({ type: "changeMediaPage", delta: 1 })}>
                <Icon name="chevronRight" />
              </button>
            </div>
          )}
          <input
            type="range"
            min={0.25}
            max={4}
            step={0.25}
            value={viewer.zoom}
            onChange={(event) => dispatch({ type: "changeMediaZoom", zoom: Number(event.target.value) })}
            aria-label="Масштаб"
          />
          <button className="primary-button" type="button" onClick={() => dispatch({ type: "closeMedia" })}>
            Закрыть
          </button>
        </>
      }
    >
      <div className="media-viewport">
        {url ? (
          <img src={url} alt={viewer.media.fileName} style={{ transform: `scale(${viewer.zoom})` }} />
        ) : (
          <div className="media-no-preview">
            <Icon name="fileText" size={36} />
            <strong>{viewer.media.fileName}</strong>
            <span>Предпросмотр для этого типа файла недоступен. Файл сохранён в проекте.</span>
          </div>
        )}
      </div>
    </Dialog>
  );
}
