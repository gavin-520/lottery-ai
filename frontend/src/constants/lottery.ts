export type LotteryTypeCode = 'SSQ' | 'FC3D'

export const LOTTERY_TYPE_LABELS: Record<LotteryTypeCode, string> = {
  SSQ: '双色球',
  FC3D: '福彩3D',
}

/** Platform default display type (UI). SSQ code paths remain available. */
export const DEFAULT_LOTTERY_TYPE: LotteryTypeCode = 'FC3D'
export const DEFAULT_LOTTERY_TYPE_LABEL = LOTTERY_TYPE_LABELS.FC3D
