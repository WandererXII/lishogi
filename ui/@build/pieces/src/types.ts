export type Ext = 'png' | 'svg';

export type PieceSetVariant = 'standard' | 'kyotoshogi' | 'chushogi' | 'dobutsu';

export type RoleDict = {
  [key: string]: string;
};

export type PieceSet = {
  name: string;
  ext: Ext;
};

export type CategorizedPieceSets = {
  regular: PieceSet[];
  bidirectional: PieceSet[];
};
