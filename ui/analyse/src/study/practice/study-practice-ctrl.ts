import { prop } from 'common/common';
import { encodeSfen } from 'common/links';
import { storedProp } from 'common/storage';
import type AnalyseCtrl from '../../ctrl';
import { readOnlyProp } from '../../util';
import type { StudyCtrl, StudyData } from '../interfaces';
import * as xhr from '../study-xhr';
import type { Goal, StudyPracticeCtrl, StudyPracticeData } from './interfaces';
import makeSound from './sound';
import makeSuccess from './study-practice-success';

export default function (
  root: AnalyseCtrl,
  studyData: StudyData,
  data: StudyPracticeData,
): StudyPracticeCtrl {
  const goal = prop<Goal>(root.data.practiceGoal!);
  const nbMoves = prop(0);
  // null = ongoing, true = win, false = fail
  const success = prop<boolean | null>(null);
  const sound = makeSound();
  const analysisUrl = prop('');
  const autoNext = storedProp('analyse.practice-auto-next', true);

  function onLoad() {
    root.showAutoShapes = readOnlyProp(true);
    root.showGauge = readOnlyProp(true);
    root.showComputer = readOnlyProp(true);
    goal(root.data.practiceGoal!);
    nbMoves(0);
    success(null);
    const chapter = studyData.chapter;
    history.replaceState(null, chapter.name, `${data.url}/${chapter.id}`);
    analysisUrl(`/analysis/standard/${encodeSfen(root.node.sfen)}?color=${root.bottomColor()}`);
  }
  onLoad();

  function computeNbMoves(): number {
    let plies = root.node.ply - root.tree.root.ply;
    if (root.bottomColor() !== root.data.player.color) plies--;
    return Math.ceil(plies / 2);
  }

  function getStudy(): StudyCtrl {
    return root.study!;
  }

  function checkSuccess(): void {
    const gamebook = getStudy().gamebookPlay();
    if (gamebook) {
      if (gamebook.state.feedback === 'end') onVictory();
      return;
    }
    if (!getStudy().data.chapter.practice) {
      saveNbMoves();
      return;
    }
    if (success() !== null) return;
    nbMoves(computeNbMoves());
    const res = success(makeSuccess(root, goal(), nbMoves()));
    if (res) onVictory();
    else if (res === false) onFailure();
  }

  function onVictory(): void {
    saveNbMoves();
    sound.success();
    if (autoNext()) setTimeout(goToNext, 1000);
  }

  function saveNbMoves(): void {
    const chapterId = getStudy().currentChapter().id;
    const former = data.completion[chapterId];
    if (typeof former === 'undefined' || nbMoves() < former) {
      data.completion[chapterId] = nbMoves();
      xhr.practiceComplete(chapterId, nbMoves());
    }
  }

  function goToNext() {
    const next = getStudy().nextChapter();
    if (next) getStudy().setChapter(next.id);
  }

  function onFailure(): void {
    root.node.fail = true;
    sound.failure();
  }

  return {
    onLoad,
    onJump() {
      // reset failure state if no failed move found in mainline history
      if (success() === false && !root.nodeList.find(n => !!n.fail)) success(null);
      checkSuccess();
    },
    onCeval: checkSuccess,
    data,
    goal,
    success,
    nbMoves,
    reset() {
      root.tree.root.children = [];
      root.userJump('');
      root.practice!.reset();
      onLoad();
      root.practice!.resume();
    },
    isSente: root.bottomIsSente,
    analysisUrl,
    autoNext,
    goToNext,
  };
}
