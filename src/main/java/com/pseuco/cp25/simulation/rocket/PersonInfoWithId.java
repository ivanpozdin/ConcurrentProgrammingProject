package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.PersonInfo;

/**
 * Wrapper class for `PersonInfo` to include id.
 *
 * @param personInfo
 * @param id
 */
public record PersonInfoWithId(PersonInfo personInfo, int id) {
}
