package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dMissingItem {

    /** hundreds | tens | units */
    private String position;
    private int number;
    private int missing;
}
